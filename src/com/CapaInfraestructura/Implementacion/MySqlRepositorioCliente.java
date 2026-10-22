package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;

public class MySqlRepositorioCliente implements IClienteRepositorio {
    private static final String COLUMNS = "id, dni, cuit, nombre, apellido, direccion, "
            + "fecha_nacimiento, localidad, activo, motivo_baja, id_negocio";

    @Override
    public void guardar(Cliente cliente) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            guardar(connection, cliente);
        } catch (SQLException exception) {
            throw databaseError("guardar cliente", exception);
        }
    }

    @Override
    public void guardar(Connection connection, Cliente cliente) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        requireCliente(cliente);
        String sql = cliente.getId() == 0
                ? "INSERT INTO clientes (dni, cuit, nombre, apellido, direccion, fecha_nacimiento, localidad, activo, motivo_baja, id_negocio) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO clientes (id, dni, cuit, nombre, apellido, direccion, fecha_nacimiento, localidad, activo, motivo_baja, id_negocio) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            if (cliente.getId() != 0) statement.setInt(index++, cliente.getId());
            bind(statement, index, cliente);
            statement.setLong(index + 9, cliente.getIdNegocio());
            statement.executeUpdate();
            if (cliente.getId() == 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) cliente.setId(keys.getInt(1));
                }
            }
        } catch (SQLException exception) {
            throw databaseError("guardar cliente", exception);
        }
    }

    @Override
    public Optional<Cliente> buscarPorId(long idNegocio, int id) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM clientes WHERE id = ? AND id_negocio = ?")) {
            statement.setInt(1, id);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseError("buscar cliente", exception);
        }
    }

    @Override
    public Optional<Cliente> buscarPorDni(long idNegocio, String dni) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (dni == null || dni.isBlank()) throw new IllegalArgumentException("El DNI es obligatorio.");
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM clientes WHERE dni = ? AND id_negocio = ?")) {
            statement.setString(1, dni.trim());
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseError("buscar cliente por DNI", exception);
        }
    }

    @Override
    public Optional<Cliente> buscarPorDniParaActualizar(Connection connection, long idNegocio, String dni) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (dni == null || dni.isBlank()) throw new IllegalArgumentException("El DNI es obligatorio.");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM clientes WHERE dni = ? AND id_negocio = ? FOR UPDATE")) {
            statement.setString(1, dni.trim());
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseError("buscar cliente por DNI", exception);
        }
    }

    @Override
    public List<Cliente> listarTodos(long idNegocio) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        List<Cliente> clientes = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM clientes WHERE id_negocio = ? ORDER BY id")) {
            statement.setLong(1, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) clientes.add(map(result));
            }
            return clientes;
        } catch (SQLException exception) {
            throw databaseError("listar clientes", exception);
        }
    }

    @Override
    public void actualizar(long idNegocio, int id, Cliente cliente) {
        requireCliente(cliente);
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        String sql = "UPDATE clientes SET dni = ?, cuit = ?, nombre = ?, apellido = ?, direccion = ?, "
                + "fecha_nacimiento = ?, localidad = ?, activo = ?, motivo_baja = ? WHERE id = ? AND id_negocio = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, 1, cliente);
            statement.setInt(10, id);
            statement.setLong(11, idNegocio);
            if (statement.executeUpdate() != 1) {
                throw new RecursoNoEncontradoException("Cliente no encontrado.");
            }
        } catch (SQLException exception) {
            throw databaseError("actualizar cliente", exception);
        }
    }

    @Override
    public void eliminar(long idNegocio, int id) {
        if (idNegocio <= 0) throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        if (id <= 0) throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        String sql = "UPDATE clientes SET activo = ?, motivo_baja = ? WHERE id = ? AND id_negocio = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, false);
            statement.setString(2, "Eliminado por el sistema");
            statement.setInt(3, id);
            statement.setLong(4, idNegocio);
            if (statement.executeUpdate() != 1) {
                throw new RecursoNoEncontradoException("Cliente no encontrado.");
            }
        } catch (SQLException exception) {
            throw databaseError("dar de baja cliente", exception);
        }
    }

    private Cliente map(ResultSet result) throws SQLException {
        Cliente cliente = new Cliente(result.getInt("id"), result.getString("dni"), result.getString("nombre"),
                result.getString("apellido"), result.getString("direccion"),
                result.getDate("fecha_nacimiento").toLocalDate(), result.getString("localidad"));
        cliente.setCuit(result.getString("cuit") == null ? "" : result.getString("cuit"));
        cliente.setActivo(result.getBoolean("activo"));
        cliente.setIdNegocio(result.getLong("id_negocio"));
        return cliente;
    }

    private static void bind(PreparedStatement statement, int start, Cliente cliente) throws SQLException {
        statement.setString(start, cliente.getDni());
        String cuit = cliente.getCuit();
        statement.setString(start + 1, (cuit == null || cuit.trim().isEmpty()) ? null : cuit.trim());
        statement.setString(start + 2, cliente.getNombre());
        statement.setString(start + 3, cliente.getApellido());
        statement.setString(start + 4, cliente.getDireccion());
        statement.setDate(start + 5, Date.valueOf(cliente.getFechaNacimiento()));
        statement.setString(start + 6, cliente.getLocalidad());
        statement.setBoolean(start + 7, cliente.isActivo());
        statement.setString(start + 8, cliente.isActivo() ? null : "Cliente dado de baja");
    }

    private static void requireCliente(Cliente cliente) {
        if (cliente == null) throw new IllegalArgumentException("El cliente no puede ser nulo.");
        if (cliente.getIdNegocio() <= 0) throw new IllegalArgumentException("El cliente debe pertenecer a un negocio válido.");
    }

    private static IllegalStateException databaseError(String operation, SQLException exception) {
        return new IllegalStateException("Error al " + operation + " en MySQL: " + exception.getMessage(), exception);
    }
}