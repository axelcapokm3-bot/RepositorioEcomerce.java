package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.Producto;

public class MySqlRepositorioProducto implements IProductoRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    public MySqlRepositorioProducto() {
        this(DatabaseConnection::getConnection);
    }

    MySqlRepositorioProducto(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    private static final Logger LOGGER = Logger.getLogger(MySqlRepositorioProducto.class.getName());

    private static final String CATEGORIA_NORMALIZADA =
            "UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(nombre, 'Á', 'A'), 'É', 'E'), 'Í', 'I'), 'Ó', 'O'), 'Ú', 'U'))";

    private static final String INSERT = "INSERT INTO productos "
            + "(nombre, precio, stock_fisico, stock_reservado, id_categoria, id_negocio, activo) "
            + "VALUES (?, ?, ?, ?, (SELECT id FROM categorias WHERE id_negocio = ? AND " + CATEGORIA_NORMALIZADA + " = ?), ?, ?)";
    private static final String SELECT_BASE = "SELECT p.id, p.nombre, p.precio, p.stock_fisico, "
            + "p.stock_reservado, p.id_negocio, p.activo, c.nombre AS categoria "
            + "FROM productos p JOIN categorias c ON c.id = p.id_categoria AND c.id_negocio = p.id_negocio ";

    @Override
    public void guardar(Producto producto) {
        try (Connection connection = connectionProvider.get()) {
            guardar(producto, connection);
        } catch (SQLException exception) {
            throw databaseError("guardar producto", exception);
        }
    }

    public void guardar(Producto producto, Connection connection) throws SQLException {
        requireProducto(producto);
        String sql = producto.getId() == 0
                ? INSERT
                : "INSERT INTO productos (id, nombre, precio, stock_fisico, stock_reservado, id_categoria, id_negocio, activo) "
                + "VALUES (?, ?, ?, ?, ?, (SELECT id FROM categorias WHERE id_negocio = ? AND " + CATEGORIA_NORMALIZADA + " = ?)), ?, ?";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            if (producto.getId() != 0) {
                statement.setInt(index++, producto.getId());
            }
            statement.setString(index++, producto.getNombre());
            statement.setBigDecimal(index++, producto.getPrecio());
            statement.setInt(index++, producto.getStockFisico());
            statement.setInt(index++, producto.getStockReservado());
            statement.setLong(index++, producto.getIdNegocio());
            statement.setString(index++, producto.getCategoria().name());
            statement.setLong(index++, producto.getIdNegocio());
            statement.setBoolean(index, producto.isActivo());
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
    public Optional<Producto> buscarPorId(long idNegocio, int id) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (Connection connection = connectionProvider.get()) {
            return buscarPorId(idNegocio, id, connection);
        } catch (SQLException exception) {
            throw databaseError("buscar producto", exception);
        }
    }

    public Optional<Producto> buscarPorId(long idNegocio, int id, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE + "WHERE p.id = ? AND p.id_negocio = ?")) {
            statement.setInt(1, id);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Producto> buscarPorIdParaUpdate(long idNegocio, int id, Connection connection) throws SQLException {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE + "WHERE p.id = ? AND p.id_negocio = ? FOR UPDATE")) {
            statement.setInt(1, id);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public long contarTotal(long idNegocio) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        String sql = "SELECT COUNT(*) FROM productos WHERE id_negocio = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw databaseError("contar total de productos", exception);
        }
        return 0L;
    }

    @Override
    public List<Producto> listarPaginado(long idNegocio, int limite, int offset) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (limite <= 0) throw new IllegalArgumentException("El límite debe ser positivo.");
        if (offset < 0) throw new IllegalArgumentException("El offset no puede ser negativo.");
        return listarPaginadoInterno(idNegocio, limite, offset, false);
    }

    @Override
    public long contarActivos(long idNegocio) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        String sql = "SELECT COUNT(*) FROM productos WHERE id_negocio = ? AND activo = 1";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw databaseError("contar productos activos", exception);
        }
        return 0L;
    }

    @Override
    public List<Producto> listarActivosPaginado(long idNegocio, int limite, int offset) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (limite <= 0) throw new IllegalArgumentException("El límite debe ser positivo.");
        if (offset < 0) throw new IllegalArgumentException("El offset no puede ser negativo.");
        return listarPaginadoInterno(idNegocio, limite, offset, true);
    }

    private List<Producto> listarPaginadoInterno(long idNegocio, int limite, int offset, boolean soloActivos) {
        List<Producto> productos = new ArrayList<>();
        String filtro = soloActivos ? " AND p.activo = 1" : "";
        String sql = SELECT_BASE + "WHERE p.id_negocio = ?" + filtro + " ORDER BY p.id LIMIT ? OFFSET ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, idNegocio);
            statement.setInt(2, limite);
            statement.setInt(3, offset);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) productos.add(map(result));
            }
            return productos;
        } catch (SQLException exception) {
            throw databaseError("listar productos paginados", exception);
        }
    }

    @Override
    public List<Producto> listarTodos(long idNegocio) {
        return listarPaginado(idNegocio, Integer.MAX_VALUE, 0);
    }

    @Override
    public List<Producto> listarActivos(long idNegocio) {
        return listarActivosPaginado(idNegocio, Integer.MAX_VALUE, 0);
    }

    @Override
    public void actualizar(long idNegocio, int id, Producto producto) {
        try (Connection connection = connectionProvider.get()) {
            actualizar(idNegocio, id, producto, connection);
        } catch (SQLException exception) {
            throw databaseError("actualizar producto", exception);
        }
    }

    public void actualizar(long idNegocio, int id, Producto producto, Connection connection) throws SQLException {
        requireProducto(producto);
        String sql = "UPDATE productos SET nombre = ?, precio = ?, stock_fisico = ?, "
                + "stock_reservado = ?, activo = ?, id_categoria = (SELECT id FROM categorias WHERE id_negocio = ? AND " + CATEGORIA_NORMALIZADA + " = ?) WHERE id = ? AND id_negocio = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, producto.getNombre());
            statement.setBigDecimal(2, producto.getPrecio());
            statement.setInt(3, producto.getStockFisico());
            statement.setInt(4, producto.getStockReservado());
            statement.setBoolean(5, producto.isActivo());
            statement.setLong(6, idNegocio);
            statement.setString(7, producto.getCategoria().name());
            statement.setInt(8, id);
            statement.setLong(9, idNegocio);
            ValidadorFilas.exigirFila(statement.executeUpdate(), "Producto", id);
        }
    }

    @Override
    public void eliminar(long idNegocio, int id) {
        try (Connection connection = connectionProvider.get()) {
            eliminar(idNegocio, id, connection);
        } catch (SQLException exception) {
            throw databaseError("eliminar producto", exception);
        }
    }

    public void eliminar(long idNegocio, int id, Connection connection) throws SQLException {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM productos WHERE id = ? AND id_negocio = ?")) {
            statement.setInt(1, id);
            statement.setLong(2, idNegocio);
            ValidadorFilas.exigirUnaFila(statement.executeUpdate(), "Producto", id);
        }
    }

private Producto map(ResultSet result) throws SQLException {
        String categoriaTexto = result.getString("categoria");
        CategoriaProducto categoriaEnum = CategoriaProducto.desdeTexto(categoriaTexto);
        if (!CategoriaProducto.esConocida(categoriaTexto)) {
            LOGGER.warning("Categoría desconocida '" + categoriaTexto + "' en negocio "
                    + result.getLong("id_negocio") + "; se mapea a OTROS.");
        }

        Producto producto = new Producto(
                result.getString("nombre"),
                result.getBigDecimal("precio"),
                result.getInt("stock_fisico"),
                result.getInt("stock_reservado"),
                result.getInt("id"),
                categoriaEnum
        );
        producto.setIdNegocio(result.getLong("id_negocio"));
        producto.setActivo(result.getBoolean("activo"));
        producto.setCategoriaOriginal(categoriaTexto);
        return producto;
    }
    private static void requireProducto(Producto producto) {
        if (producto == null) throw new IllegalArgumentException("El producto no puede ser nulo.");
        if (producto.getIdNegocio() <= 0) throw new IllegalArgumentException("El producto debe pertenecer a un negocio válido.");
    }

    private static IllegalStateException databaseError(String operation, SQLException exception) {
        return new IllegalStateException("Error al " + operation + " en MySQL: " + exception.getMessage(), exception);
    }
}
