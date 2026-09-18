package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;

public class MySqlRepositorioVenta implements IVentaRepository {
    private static final String DETALLE_TABLE = "detalle_ventas";
    private static final String VENTA_COLUMNS = "id, id_cliente, fecha_compra, id_estado_venta, id_tipo_pago";

    @Override
    public void guardar(Venta venta) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            guardar(venta, connection);
        } catch (SQLException exception) {
            throw databaseError("guardar venta", exception);
        }
    }

    public void guardar(Venta venta, Connection connection) throws SQLException {
        requireVenta(venta);
        persistirVenta(venta, false, 0, connection);
    }

    @Override
    public Optional<Venta> buscarPorId(int id) {
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (Connection connection = DatabaseConnection.getConnection()) {
            return buscarPorId(id, connection);
        } catch (SQLException exception) {
            throw databaseError("buscar venta", exception);
        }
    }

    public Optional<Venta> buscarPorId(int id, Connection connection) throws SQLException {
        String sql = "SELECT v." + VENTA_COLUMNS.replace(", ", ", v.")
                + ", ev.nombre AS estado, tp.nombre AS tipo_pago FROM ventas v "
                + "JOIN estado_venta ev ON ev.id = v.id_estado_venta "
                + "JOIN tipo_pago tp ON tp.id = v.id_tipo_pago WHERE v.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Venta venta = mapVenta(result);
                cargarDetalles(connection, venta);
                return Optional.of(venta);
            }
        }
    }

    @Override
    public List<Venta> listarTodas() {
        List<Venta> ventas = new ArrayList<>();
        String sql = "SELECT v." + VENTA_COLUMNS.replace(", ", ", v.")
                + ", ev.nombre AS estado, tp.nombre AS tipo_pago FROM ventas v "
                + "JOIN estado_venta ev ON ev.id = v.id_estado_venta "
                + "JOIN tipo_pago tp ON tp.id = v.id_tipo_pago ORDER BY v.id";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Venta venta = mapVenta(result);
                cargarDetalles(connection, venta);
                ventas.add(venta);
            }
            return ventas;
        } catch (SQLException exception) {
            throw databaseError("listar ventas", exception);
        }
    }

    @Override
    public void actualizar(int id, Venta venta) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            actualizar(id, venta, connection);
        } catch (SQLException exception) {
            throw databaseError("actualizar venta", exception);
        }
    }

    public void actualizar(int id, Venta venta, Connection connection) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        requireVenta(venta);
        persistirVenta(venta, true, id, connection);
    }

    private void persistirVenta(Venta venta, boolean update, int id, Connection connection) throws SQLException {
        String insertVenta = "INSERT INTO ventas (id_cliente, fecha_compra, id_estado_venta, id_tipo_pago, total) "
                + "VALUES (?, ?, (SELECT id FROM estado_venta WHERE nombre = ?), "
                + "(SELECT id FROM tipo_pago WHERE nombre = ?), ?)";
        String updateVenta = "UPDATE ventas SET id_cliente = ?, fecha_compra = ?, "
                + "id_estado_venta = (SELECT id FROM estado_venta WHERE nombre = ?), "
                + "id_tipo_pago = (SELECT id FROM tipo_pago WHERE nombre = ?), total = ? WHERE id = ?";
        
        try (PreparedStatement ventaStatement = connection.prepareStatement(
                update ? updateVenta : insertVenta, Statement.RETURN_GENERATED_KEYS)) {
            bindVenta(ventaStatement, venta, update, id);
            if (update) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + DETALLE_TABLE + " WHERE id_venta = ?")) {
                    delete.setInt(1, id);
                    delete.executeUpdate();
                }
            }
            ventaStatement.executeUpdate();
            if (!update) {
                try (ResultSet keys = ventaStatement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("MySQL no devolvió el ID de la venta.");
                    venta.setId(keys.getInt(1));
                }
            }
            insertarDetalles(connection, venta);
        }
    }

    private void insertarDetalles(Connection connection, Venta venta) throws SQLException {
        String sql = "INSERT INTO " + DETALLE_TABLE + " "
                + "(id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DetalleVenta detalle : venta.getDetalles()) {
                statement.setInt(1, venta.getId());
                statement.setInt(2, detalle.getIdProducto());
                statement.setInt(3, detalle.getCantidad());
                statement.setBigDecimal(4, detalle.getPrecioUnitario());
                statement.setBigDecimal(5, detalle.getSubtotal());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void cargarDetalles(Connection connection, Venta venta) throws SQLException {
        String sql = "SELECT id_producto, cantidad, precio_unitario FROM " + DETALLE_TABLE + " WHERE id_venta = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, venta.getId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    venta.getDetalles().add(new DetalleVenta(result.getInt("id_producto"),
                            result.getBigDecimal("precio_unitario"), result.getInt("cantidad")));
                }
            }
        }
    }

    private Venta mapVenta(ResultSet result) throws SQLException {
        Venta venta = new Venta(result.getInt("id_cliente"), TipoDePago.valueOf(result.getString("tipo_pago")));
        venta.setId(result.getInt("id"));
        restaurarEstado(venta, EstadoVenta.valueOf(result.getString("estado")));
        return venta;
    }

    private static void restaurarEstado(Venta venta, EstadoVenta estado) {
        if (estado == EstadoVenta.PAGADO) venta.Pagar();
        if (estado == EstadoVenta.ENVIADO) { venta.Pagar(); venta.Enviar(); }
        if (estado == EstadoVenta.CANCELADO) { venta.Pagar(); venta.Cancelar(); }
    }

    private static void bindVenta(PreparedStatement statement, Venta venta, boolean update, int id) throws SQLException {
        statement.setInt(1, venta.getIdCliente());
        statement.setTimestamp(2, Timestamp.valueOf(venta.getFechaCompra()));
        statement.setString(3, venta.getEstadoCompra().name());
        statement.setString(4, venta.getTipoPago().name());
        statement.setBigDecimal(5, venta.calculoTotal());
        if (update) statement.setInt(6, id);
    }

    private static void requireVenta(Venta venta) {
        if (venta == null) throw new IllegalArgumentException("La venta no puede ser nula.");
    }

    private static IllegalStateException databaseError(String operation, SQLException exception) {
        return new IllegalStateException("Error al " + operation + " en MySQL: " + exception.getMessage(), exception);
    }
}