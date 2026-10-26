package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaDominio.Entidades.EstadoCuenta;
import com.CapaDominio.Excepciones.IdentidadDuplicadaException;

public class MySqlRepositorioCuenta implements ICuentaRepositorio {
    private static final String COLUMNAS = "id, nombre_cuenta, correo_electronico, contrasenia, activo";

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta == null) {
            throw new IllegalArgumentException("La cuenta no puede ser nula.");
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            guardar(connection, cuenta);
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al guardar cuenta en MySQL.", exception);
        }
    }

    @Override
    public void guardar(Connection connection, Cuenta cuenta) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (cuenta == null) {
            throw new IllegalArgumentException("La cuenta no puede ser nula.");
        }
        String sql = cuenta.getId() == 0
                ? "INSERT INTO cuentas (nombre_cuenta, correo_electronico, contrasenia, activo) "
                + "VALUES (?, ?, ?, ?)"
                : "INSERT INTO cuentas (id, nombre_cuenta, correo_electronico, contrasenia, activo) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int indice = 1;
            if (cuenta.getId() != 0) {
                statement.setLong(indice++, cuenta.getId());
            }
            statement.setString(indice++, cuenta.getNombreCuenta());
            statement.setString(indice++, cuenta.getCorreoElectronico());
            statement.setString(indice++, cuenta.getHashContrasenia());
            statement.setBoolean(indice, cuenta.estaActiva());
            statement.executeUpdate();
            if (cuenta.getId() == 0) {
                try (ResultSet claves = statement.getGeneratedKeys()) {
                    if (claves.next()) {
                        cuenta.setId(claves.getLong(1));
                    }
                }
            }
        } catch (SQLIntegrityConstraintViolationException duplicado) {
            throw new IdentidadDuplicadaException(
                    "El correo electrónico ya está registrado.", duplicado);
        } catch (SQLException exception) {
            if (esViolacionUnica(exception)) {
                throw new IdentidadDuplicadaException(
                        "El correo electrónico ya está registrado.", exception);
            }
            throw new IllegalStateException("Error al guardar cuenta en MySQL.", exception);
        }
    }

    @Override
    public Optional<Cuenta> buscarPorId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapear(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar cuenta en MySQL.", exception);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Cuenta> buscarPorCorreo(String correoElectronico) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return buscarPorCorreo(connection, correoElectronico);
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar cuenta en MySQL.", exception);
        }
    }

    @Override
    public Optional<Cuenta> buscarPorCorreo(Connection connection, String correoElectronico) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNAS + " FROM cuentas WHERE correo_electronico = ?")) {
            statement.setString(1, correoElectronico.trim().toLowerCase());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapear(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar cuenta en MySQL.", exception);
        }
        return Optional.empty();
    }

    @Override
    public boolean existeCorreo(String correoElectronico) {
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM cuentas WHERE correo_electronico = ? LIMIT 1")) {
            statement.setString(1, correoElectronico.trim().toLowerCase());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al verificar correo en MySQL.", exception);
        }
    }

    @Override
    public long contarCuentas() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM cuentas");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al contar cuentas en MySQL.", exception);
        }
        return 0;
    }

    private Cuenta mapear(ResultSet result) throws SQLException {
        Cuenta cuenta = new Cuenta(
                result.getLong("id"),
                result.getString("nombre_cuenta"),
                result.getString("correo_electronico"),
                result.getString("contrasenia"));
        cuenta.setEstado(result.getBoolean("activo") ? EstadoCuenta.ACTIVA : EstadoCuenta.BAJA);
        return cuenta;
    }

    private boolean esViolacionUnica(SQLException exception) {
        SQLException actual = exception;
        while (actual != null) {
            if (actual instanceof SQLIntegrityConstraintViolationException
                    || ("23000".equals(actual.getSQLState()) && actual.getErrorCode() == 1062)) {
                return true;
            }
            actual = actual.getNextException();
        }
        return false;
    }
}
