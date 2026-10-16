package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.ICuentaNegocioRepositorio;
import com.CapaDominio.Entidades.EstadoCuenta;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaDominio.Entidades.VinculoCuentaNegocio;

public class MySqlRepositorioCuentaNegocio implements ICuentaNegocioRepositorio {
    private static final String COLUMNAS = "id, id_cuenta, id_negocio, id_cliente, rol, estado";

    @Override
    public void guardar(VinculoCuentaNegocio vinculo) {
        if (vinculo == null) {
            throw new IllegalArgumentException("El vínculo no puede ser nulo.");
        }
        String sql = vinculo.getId() == 0
                ? "INSERT INTO cuentas_negocio (id_cuenta, id_negocio, id_cliente, rol, estado) "
                + "VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO cuentas_negocio (id, id_cuenta, id_negocio, id_cliente, rol, estado) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int indice = 1;
            if (vinculo.getId() != 0) {
                statement.setInt(indice++, vinculo.getId());
            }
            statement.setInt(indice++, vinculo.getIdCuenta());
            statement.setLong(indice++, vinculo.getIdNegocio());
            statement.setInt(indice++, vinculo.getIdCliente());
            statement.setString(indice++, vinculo.getRol().name());
            statement.setString(indice, vinculo.getEstado().name());
            statement.executeUpdate();
            if (vinculo.getId() == 0) {
                try (ResultSet claves = statement.getGeneratedKeys()) {
                    if (claves.next()) {
                        vinculo.setId(claves.getInt(1));
                    }
                }
            }
        } catch (SQLIntegrityConstraintViolationException duplicado) {
            throw new IllegalStateException("La cuenta ya está vinculada a este negocio.", duplicado);
        } catch (SQLException exception) {
            if (esViolacionUnica(exception)) {
                throw new IllegalStateException("La cuenta ya está vinculada a este negocio.", exception);
            }
            throw new IllegalStateException("Error al guardar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    @Override
    public Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(int idCuenta, long idNegocio) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ?")) {
            statement.setInt(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapear(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar el vínculo cuenta-negocio en MySQL.", exception);
        }
        return Optional.empty();
    }

    @Override
    public List<VinculoCuentaNegocio> listarPorCuenta(int idCuenta) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        List<VinculoCuentaNegocio> vinculos = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_cuenta = ? ORDER BY id_negocio")) {
            statement.setInt(1, idCuenta);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    vinculos.add(mapear(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al listar los vínculos de la cuenta en MySQL.", exception);
        }
        return vinculos;
    }

    @Override
    public boolean existeVinculo(int idCuenta, long idNegocio) {
        if (idCuenta <= 0 || idNegocio <= 0) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ? LIMIT 1")) {
            statement.setInt(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al verificar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    private VinculoCuentaNegocio mapear(ResultSet result) throws SQLException {
        VinculoCuentaNegocio vinculo = new VinculoCuentaNegocio(
                result.getInt("id"),
                result.getInt("id_cuenta"),
                result.getLong("id_negocio"),
                result.getInt("id_cliente"),
                RolUsuario.desdeTexto(result.getString("rol")));
        vinculo.setEstado(EstadoCuenta.desdeTexto(result.getString("estado")));
        return vinculo;
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
