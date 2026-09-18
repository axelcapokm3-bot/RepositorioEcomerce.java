package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.Producto;

public class MySqlRepositorioProducto implements IProductoRepository {



    private static final String INSERT = "INSERT INTO productos "
            + "(nombre, precio, stock_fisico, stock_reservado, id_categoria) "
            + "VALUES (?, ?, ?, ?, (SELECT id FROM categorias WHERE nombre = ?))";
    private static final String SELECT_BASE = "SELECT p.id, p.nombre, p.precio, p.stock_fisico, "
            + "p.stock_reservado, c.nombre AS categoria "
            + "FROM productos p JOIN categorias c ON c.id = p.id_categoria ";

    @Override
    public void guardar(Producto producto) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            guardar(producto, connection);
        } catch (SQLException exception) {
            throw databaseError("guardar producto", exception);
        }
    }

    public void guardar(Producto producto, Connection connection) throws SQLException {
        requireProducto(producto);
        String sql = producto.getId() == 0
                ? INSERT
                : "INSERT INTO productos (id, nombre, precio, stock_fisico, stock_reservado, id_categoria) "
                + "VALUES (?, ?, ?, ?, ?, (SELECT id FROM categorias WHERE UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(nombre, 'Á', 'A'), 'É', 'E'), 'Í', 'I'), 'Ó', 'O'), 'Ú', 'U')) = ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            if (producto.getId() != 0) {
                statement.setInt(index++, producto.getId());
            }
            statement.setString(index++, producto.getNombre());
            statement.setBigDecimal(index++, producto.getPrecio());
            statement.setInt(index++, producto.getStockFisico());
            statement.setInt(index++, producto.getStockReservado());
            statement.setString(index, producto.getCategoria().name());
            statement.executeUpdate();
            if (producto.getId() == 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        producto.setId(keys.getInt(1));
                    }
                }
            }
        }
    }

    @Override
    public Optional<Producto> buscarPorId(int id) {
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (Connection connection = DatabaseConnection.getConnection()) {
            return buscarPorId(id, connection);
        } catch (SQLException exception) {
            throw databaseError("buscar producto", exception);
        }
    }

    public Optional<Producto> buscarPorId(int id, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE + "WHERE p.id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public Optional<Producto> buscarPorIdParaUpdate(int id, Connection connection) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE + "WHERE p.id = ? FOR UPDATE")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Producto> listarTodos() {
        List<Producto> productos = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BASE + "ORDER BY p.id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) productos.add(map(result));
            return productos;
        } catch (SQLException exception) {
            throw databaseError("listar productos", exception);
        }
    }

    @Override
    public void actualizar(int id, Producto producto) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            actualizar(id, producto, connection);
        } catch (SQLException exception) {
            throw databaseError("actualizar producto", exception);
        }
    }

    public void actualizar(int id, Producto producto, Connection connection) throws SQLException {
        requireProducto(producto);
        String sql = "UPDATE productos SET nombre = ?, precio = ?, stock_fisico = ?, "
                + "stock_reservado = ?, id_categoria = (SELECT id FROM categorias WHERE nombre = ?) WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, producto.getNombre());
            statement.setBigDecimal(2, producto.getPrecio());
            statement.setInt(3, producto.getStockFisico());
            statement.setInt(4, producto.getStockReservado());
            statement.setString(5, producto.getCategoria().name());
            statement.setInt(6, id);
            statement.executeUpdate();
        }
    }

    @Override
    public void eliminar(int id) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            eliminar(id, connection);
        } catch (SQLException exception) {
            throw databaseError("eliminar producto", exception);
        }
    }

    public void eliminar(int id, Connection connection) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM productos WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

private Producto map(ResultSet result) throws SQLException {
        String categoriaTexto = result.getString("categoria");
        
        // Limpiamos espacios, pasamos a mayúsculas y quitamos tildes
        String categoriaLimpia = categoriaTexto.trim().toUpperCase()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace(" ", "_"); 

        CategoriaProducto categoriaEnum = CategoriaProducto.valueOf(categoriaLimpia);

        return new Producto(
                result.getString("nombre"), 
                result.getBigDecimal("precio"),
                result.getInt("stock_fisico"), 
                result.getInt("stock_reservado"), 
                result.getInt("id"),
                categoriaEnum
        );
    }
    private static void requireProducto(Producto producto) {
        if (producto == null) throw new IllegalArgumentException("El producto no puede ser nulo.");
    }

    private static IllegalStateException databaseError(String operation, SQLException exception) {
        return new IllegalStateException("Error al " + operation + " en MySQL: " + exception.getMessage(), exception);
    }
}