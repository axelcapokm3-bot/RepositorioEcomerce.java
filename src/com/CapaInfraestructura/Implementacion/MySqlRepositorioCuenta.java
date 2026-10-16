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

public class MySqlRepositorioCuenta implements ICuentaRepositorio {
    private static final String COLUMNAS = "id, nombre_cuenta, correo_electronico, contrasenia, estado";

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta == null) {
            throw new IllegalArgumentException("La cuenta no puede ser nula.");
        }
        String sql = cuenta.getId() == 0
                ? "INSERT INTO cuentas (nombre_cuenta, correo_electronico, contrasenia, estado) "
                + "VALUES (?, ?, ?, ?)"
                : "INSERT INTO cuentas (id, nombre_cuenta, correo_electronico, contrasenia, estado) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int indice = 1;
            if (cuenta.getId() != 0) {
                statement.setInt(indice++, cuenta.getId());
            }
            statement.setString(indice++, cuenta.getNombreCuenta());
            statement.setString(indice++, cuenta.getCorreoElectronico());
            statement.setString(indice++, cuenta.getHashContrasenia());
            statement.setString(indice, cuenta.getEstado().name());
            statement.executeUpdate();
            if (cuenta.getId() == 0) {
                try (ResultSet claves = statement.getGeneratedKeys()) {
                    if (claves.next()) {
                        cuenta.setId(claves.getInt(1));
                    }
                }
            }
        } catch (SQLIntegrityConstraintViolationException duplicado) {
            throw new IllegalStateException("El correo electrónico ya está registrado.", duplicado);
        } catch (SQLException exception) {
            if (esViolacionUnica(exception)) {
                throw new IllegalStateException("El correo electrónico ya está registrado.", exception);
            }
            throw new IllegalStateException("Error al guardar cuenta en MySQL.", exception);
        }
    }

    @Override
    public Optional<Cuenta> buscarPorId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas WHERE id = ?")) {
            statement.setInt(1, id);
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
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
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
                result.getInt("id"),
                result.getString("nombre_cuenta"),
                result.getString("correo_electronico"),
                result.getString("contrasenia"));
        cuenta.setEstado(EstadoCuenta.desdeTexto(result.getString("estado")));
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
