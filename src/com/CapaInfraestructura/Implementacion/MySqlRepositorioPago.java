package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.Pago;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MySqlRepositorioPago implements IPagoRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private static final String COLUMNAS = "id, id_venta, pasarela, mp_payment_id, order_id, preferencia_id, " +
            "estado, monto, moneda, url_pago, raw_response, idempotency_key, fecha_creacion, fecha_actualizacion, id_negocio";
    private final ConnectionProvider connectionProvider;

    public MySqlRepositorioPago() {
        this(DatabaseConnection::getConnection);
    }

    MySqlRepositorioPago(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public void insertar(Pago pago) {
        if (pago == null) throw new IllegalArgumentException("El pago no puede ser nulo.");
        if (pago.getIdNegocio() <= 0) throw new IllegalArgumentException("El pago debe pertenecer a un negocio válido.");
        String sql = "INSERT INTO pagos (id_venta, pasarela, mp_payment_id, order_id, preferencia_id, estado, monto, " +
                "moneda, url_pago, raw_response, idempotency_key, fecha_creacion, fecha_actualizacion, id_negocio) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, pago.getIdVenta());
            statement.setString(2, pago.getPasarela());
            statement.setString(3, pago.getMpPaymentId());
            statement.setString(4, pago.getOrderId());
            statement.setString(5, pago.getPreferenciaId());
            statement.setString(6, pago.getEstado().name());
            statement.setBigDecimal(7, pago.getMonto());
            statement.setString(8, pago.getMoneda() == null ? "ARS" : pago.getMoneda());
            statement.setString(9, pago.getUrlPago());
            statement.setString(10, pago.getRawResponse());
            statement.setString(11, pago.getClaveIdempotenciaPasarela());
            setDateTime(statement, 12, pago.getFechaCreacion() == null ? LocalDateTime.now() : pago.getFechaCreacion());
            setDateTime(statement, 13, pago.getFechaActualizacion());
            statement.setLong(14, pago.getIdNegocio());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) pago.setId(keys.getInt(1));
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            if (recuperarPagoExistente(pago)) return;
            throw new RuntimeException("Conflicto de integridad al insertar el pago", e);
        } catch (SQLException e) {
            if (esViolacionUnica(e) && recuperarPagoExistente(pago)) return;
            throw new RuntimeException("Error al insertar el pago: " + e.getMessage(), e);
        }
    }

    private boolean recuperarPagoExistente(Pago pago) {
        if (pago.getClaveIdempotenciaPasarela() != null) {
            Optional<Pago> pagoExistente = buscarPorClaveIdempotenciaPasarela(
                    pago.getIdNegocio(), pago.getClaveIdempotenciaPasarela());
            if (pagoExistente.isPresent()) {
                pago.setId(pagoExistente.get().getId());
                return true;
            }
        }
        if (pago.getMpPaymentId() != null) {
            Optional<Pago> pagoExistente = buscarPorMpPaymentId(pago.getMpPaymentId());
            if (pagoExistente.isPresent()) {
                pago.setId(pagoExistente.get().getId());
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Pago> buscarPorVenta(long idNegocio, int idVenta) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_venta = ? AND id_negocio = ? ORDER BY fecha_creacion, id";
        List<Pago> pagos = new ArrayList<>();
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, idVenta);
            statement.setLong(2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) pagos.add(mapPago(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar pagos de la venta: " + e.getMessage(), e);
        }
        return pagos;
    }

    @Override
    public Optional<Pago> buscarPorMpPaymentId(String mpPaymentId) {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE mp_payment_id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mpPaymentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return Optional.of(mapPago(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar pago por identificador de pasarela: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Pago> buscarPorOrderId(String orderId) {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE order_id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPago(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar pago por Order ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Pago> buscarPorClaveIdempotenciaPasarela(long idNegocio, String claveIdempotenciaPasarela) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE idempotency_key = ? AND id_negocio = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claveIdempotenciaPasarela);
            statement.setLong(2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return Optional.of(mapPago(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error al buscar pago por clave de idempotencia de pasarela: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void actualizarCheckout(int id, String externalId, String orderId, String urlPago,
                                   EstadoPago estado, String rawResponse) {
        String sql = "UPDATE pagos SET mp_payment_id = ?, order_id = ?, url_pago = ?, estado = ?, " +
                "raw_response = ?, fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, externalId);
            statement.setString(2, orderId);
            statement.setString(3, urlPago);
            statement.setString(4, estado.name());
            statement.setString(5, rawResponse);
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(7, id);
            verificarActualizacion(statement.executeUpdate(), id);
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar los datos del checkout: " + e.getMessage(), e);
        }
    }

    @Override
    public void actualizarEstado(int id, EstadoPago estado, String rawResponse) {
        String sql = "UPDATE pagos SET estado = ?, raw_response = ?, fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, estado.name());
            statement.setString(2, rawResponse);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(4, id);
            verificarActualizacion(statement.executeUpdate(), id);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar estado del pago: " + e.getMessage(), e);
        }
    }

    @Override
    public void actualizarWebhook(int id, String mpPaymentId, String orderId,
                                  EstadoPago estado, String rawResponse) {
        String sql = "UPDATE pagos SET mp_payment_id = ?, order_id = ?, estado = ?, raw_response = ?, " +
                "fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mpPaymentId);
            statement.setString(2, orderId);
            statement.setString(3, estado.name());
            statement.setString(4, rawResponse);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(6, id);
            verificarActualizacion(statement.executeUpdate(), id);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar pago desde webhook: " + e.getMessage(), e);
        }
    }

    private void verificarActualizacion(int filasActualizadas, int idPago) {
        if (filasActualizadas != 1) {
            throw new IllegalStateException("No existe un pago con ID " + idPago + " para actualizar.");
        }
    }

    private Pago mapPago(ResultSet rs) throws SQLException {
        Timestamp creado = rs.getTimestamp("fecha_creacion");
        Timestamp actualizado = rs.getTimestamp("fecha_actualizacion");
        Pago pago = new Pago(
                rs.getInt("id"), rs.getInt("id_venta"), rs.getString("pasarela"),
                rs.getString("mp_payment_id"), rs.getString("preferencia_id"),
                rs.getString("order_id"), EstadoPago.valueOf(rs.getString("estado")),
                rs.getBigDecimal("monto"), rs.getString("moneda"), rs.getString("url_pago"),
                rs.getString("raw_response"), rs.getString("idempotency_key"),
                creado == null ? null : creado.toLocalDateTime(),
                actualizado == null ? null : actualizado.toLocalDateTime());
        pago.setIdNegocio(rs.getLong("id_negocio"));
        return pago;
    }

    private void setDateTime(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) statement.setTimestamp(index, null);
        else statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    private boolean esViolacionUnica(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current instanceof SQLIntegrityConstraintViolationException
                    || "23000".equals(current.getSQLState()) && current.getErrorCode() == 1062) return true;
            current = current.getNextException();
        }
        return false;
    }
}
