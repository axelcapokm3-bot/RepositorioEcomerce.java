package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.ICuentaNegocioRepositorio;
import com.CapaDominio.Entidades.EstadoCuenta;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaDominio.Entidades.VinculoCuentaNegocio;

public class MySqlRepositorioCuentaNegocio implements ICuentaNegocioRepositorio {
    private static final String COLUMNAS = "id_cuenta, id_negocio, id_cliente, rol_usuario, activo";
    private static final String MENSAJE_DUPLICADO =
            "La cuenta ya está vinculada a este negocio o el cliente ya tiene otra cuenta vinculada.";

    @Override
    public void guardar(VinculoCuentaNegocio vinculo) {
        if (vinculo == null) {
            throw new IllegalArgumentException("El vínculo no puede ser nulo.");
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            guardar(connection, vinculo);
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al guardar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    @Override
    public void guardar(Connection connection, VinculoCuentaNegocio vinculo) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (vinculo == null) {
            throw new IllegalArgumentException("El vínculo no puede ser nulo.");
        }
        try {
            Optional<VinculoCuentaNegocio> actual =
                    buscarPorCuentaYNegocio(connection, vinculo.getIdCuenta(), vinculo.getIdNegocio());
            if (actual.isPresent()) {
                if (actual.get().estaActivo()) {
                    throw new IllegalStateException(MENSAJE_DUPLICADO);
                }
                Optional<VinculoCuentaNegocio> porCliente = buscarPorNegocioYCliente(
                        connection, vinculo.getIdNegocio(), vinculo.getIdCliente());
                if (porCliente.isPresent() && porCliente.get().getIdCuenta() != vinculo.getIdCuenta()) {
                    throw new IllegalStateException(MENSAJE_DUPLICADO);
                }
                reactivar(connection, vinculo);
                return;
            }
            if (buscarPorNegocioYCliente(connection, vinculo.getIdNegocio(), vinculo.getIdCliente()).isPresent()) {
                throw new IllegalStateException(MENSAJE_DUPLICADO);
            }
            insertar(connection, vinculo);
        } catch (SQLIntegrityConstraintViolationException duplicado) {
            throw new IllegalStateException(MENSAJE_DUPLICADO, duplicado);
        } catch (SQLException exception) {
            if (esViolacionUnica(exception)) {
                throw new IllegalStateException(MENSAJE_DUPLICADO, exception);
            }
            throw new IllegalStateException("Error al guardar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    @Override
    public Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(long idCuenta, long idNegocio) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ?")) {
            statement.setLong(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapear(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    @Override
    public Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(Connection connection,
                                                                 long idCuenta,
                                                                 long idNegocio) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ? FOR UPDATE")) {
            statement.setLong(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapear(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    @Override
    public Optional<VinculoCuentaNegocio> buscarPorNegocioYCliente(Connection connection,
                                                                  long idNegocio,
                                                                  int idCliente) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio debe ser mayor a cero.");
        }
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID de cliente debe ser mayor a cero.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_negocio = ? AND id_cliente = ? FOR UPDATE")) {
            statement.setLong(1, idNegocio);
            statement.setInt(2, idCliente);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapear(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al buscar el vínculo del cliente en MySQL.", exception);
        }
    }

    @Override
    public List<VinculoCuentaNegocio> listarPorCuenta(long idCuenta) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        List<VinculoCuentaNegocio> vinculos = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNAS + " FROM cuentas_negocio WHERE id_cuenta = ? ORDER BY id_negocio")) {
            statement.setLong(1, idCuenta);
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
    public boolean existeVinculo(long idCuenta, long idNegocio) {
        if (idCuenta <= 0 || idNegocio <= 0) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ? AND activo = 1 LIMIT 1")) {
            statement.setLong(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Error al verificar el vínculo cuenta-negocio en MySQL.", exception);
        }
    }

    private void insertar(Connection connection, VinculoCuentaNegocio vinculo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cuentas_negocio (id_cuenta, id_negocio, id_cliente, rol_usuario, activo) "
                + "VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, vinculo.getIdCuenta());
            statement.setLong(2, vinculo.getIdNegocio());
            statement.setInt(3, vinculo.getIdCliente());
            statement.setString(4, vinculo.getRol().name());
            statement.setBoolean(5, vinculo.estaActivo());
            statement.executeUpdate();
        }
    }

    private void reactivar(Connection connection, VinculoCuentaNegocio vinculo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE cuentas_negocio SET id_cliente = ?, rol_usuario = ?, activo = ? "
                + "WHERE id_cuenta = ? AND id_negocio = ?")) {
            statement.setInt(1, vinculo.getIdCliente());
            statement.setString(2, vinculo.getRol().name());
            statement.setBoolean(3, vinculo.estaActivo());
            statement.setLong(4, vinculo.getIdCuenta());
            statement.setLong(5, vinculo.getIdNegocio());
            statement.executeUpdate();
        }
    }

    private VinculoCuentaNegocio mapear(ResultSet result) throws SQLException {
        VinculoCuentaNegocio vinculo = new VinculoCuentaNegocio(
                result.getLong("id_cuenta"),
                result.getLong("id_negocio"),
                result.getInt("id_cliente"),
                RolUsuario.desdeTexto(result.getString("rol_usuario")));
        vinculo.setEstado(result.getBoolean("activo") ? EstadoCuenta.ACTIVA : EstadoCuenta.BAJA);
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
