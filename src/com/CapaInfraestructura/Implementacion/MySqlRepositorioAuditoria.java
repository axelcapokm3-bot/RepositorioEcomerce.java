package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio de auditoría persistente en MySQL utilizando JDBC y HikariCP.
 * Sigue estrictamente el principio Append-Only.
 */
public class MySqlRepositorioAuditoria implements IAuditoriaRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    public MySqlRepositorioAuditoria() {
        this(DatabaseConnection::getConnection);
    }

    MySqlRepositorioAuditoria(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    private static final String INSERT = "INSERT INTO auditoria "
            + "(id_negocio, accion, entidad, id_entidad, usuario, ip_origen, user_agent, resultado, descripcion, detalle) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BASE = "SELECT a.id, a.id_negocio, a.accion, a.entidad, a.id_entidad, "
            + "a.usuario, a.ip_origen, a.user_agent, a.resultado, a.descripcion, a.detalle, a.fecha FROM auditoria a ";

    private static final String FILTRO_NEGOCIO = "a.id_negocio = ?";

    private static void bindNegocio(PreparedStatement statement, int start, long idNegocio) throws SQLException {
        statement.setLong(start, idNegocio);
    }

    private static void exigirNegocio(long idNegocio) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio es obligatorio para consultar auditoría.");
        }
    }

    @Override
    public void guardar(Auditoria auditoria) throws SQLException {
        if (auditoria == null) {
            throw new IllegalArgumentException("El registro de auditoría no puede ser nulo.");
        }
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            if (auditoria.getIdNegocio() > 0) {
                statement.setLong(1, auditoria.getIdNegocio());
            } else {
                statement.setNull(1, java.sql.Types.BIGINT);
            }
            statement.setString(2, auditoria.getAccion() != null ? auditoria.getAccion().name() : AccionAuditoria.CREAR.name());
            statement.setString(3, auditoria.getEntidad() != null ? auditoria.getEntidad() : "Venta");
            statement.setInt(4, auditoria.getIdEntidad());
            statement.setString(5, auditoria.getUsuario());
            statement.setString(6, auditoria.getIpOrigen());
            statement.setString(7, auditoria.getUserAgent());
            statement.setString(8, auditoria.getResultado() != null ? auditoria.getResultado() : "OK");
            statement.setString(9, auditoria.getDescripcion() != null ? auditoria.getDescripcion() : "");
            statement.setString(10, auditoria.getDetalle());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    auditoria.setId(keys.getLong(1));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al guardar auditoría en MySQL: " + SanitizadorLog.error(exception));
            throw exception;
        }
    }

    @Override
    public List<Auditoria> buscarPorEntidad(long idNegocio, String entidad, int idEntidad) {
        exigirNegocio(idNegocio);
        List<Auditoria> lista = new ArrayList<>();
        String filtro = filtroEntidad(entidad);
        if (filtro == null) {
            return lista;
        }
        String sql = SELECT_BASE + "WHERE a.entidad = ? AND a.id_entidad = ? AND " + filtro
                + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entidad);
            statement.setInt(2, idEntidad);
            statement.setLong(3, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al buscar auditoría por entidad: " + SanitizadorLog.error(exception));
        }
        return lista;
    }

    private static String filtroEntidad(String entidad) {
        if ("Venta".equalsIgnoreCase(entidad)
                || "Producto".equalsIgnoreCase(entidad)
                || "Cliente".equalsIgnoreCase(entidad)
                || "Pago".equalsIgnoreCase(entidad)) {
            return "a.id_negocio = ?";
        }
        return null;
    }

    @Override
    public List<Auditoria> buscarPorUsuario(long idNegocio, String usuario) {
        exigirNegocio(idNegocio);
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.usuario = ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usuario);
            bindNegocio(statement, 2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al buscar auditoría por usuario: " + SanitizadorLog.error(exception));
        }
        return lista;
    }

    @Override
    public List<Auditoria> buscarPorAccion(long idNegocio, AccionAuditoria accion) {
        exigirNegocio(idNegocio);
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.accion = ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accion.name());
            bindNegocio(statement, 2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al buscar auditoría por acción: " + SanitizadorLog.error(exception));
        }
        return lista;
    }

    @Override
    public List<Auditoria> buscarPorRangoFecha(long idNegocio, LocalDateTime desde, LocalDateTime hasta) {
        exigirNegocio(idNegocio);
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.fecha BETWEEN ? AND ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 1000";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, desde != null ? Timestamp.valueOf(desde) : Timestamp.valueOf(LocalDateTime.now().minusYears(1)));
            statement.setTimestamp(2, hasta != null ? Timestamp.valueOf(hasta) : Timestamp.valueOf(LocalDateTime.now()));
            bindNegocio(statement, 3, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al buscar auditoría por rango de fecha: " + SanitizadorLog.error(exception));
        }
        return lista;
    }

    @Override
    public long contarTotal(long idNegocio) {
        exigirNegocio(idNegocio);
        String sql = "SELECT COUNT(*) FROM auditoria a WHERE " + FILTRO_NEGOCIO;
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNegocio(statement, 1, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException exception) {
            System.err.println("Error al contar total de auditorías: " + SanitizadorLog.error(exception));
        }
        return 0L;
    }

    @Override
    public List<Auditoria> listarPaginado(long idNegocio, int limite, int offset) {
        exigirNegocio(idNegocio);
        if (limite <= 0) throw new IllegalArgumentException("El límite debe ser positivo.");
        if (offset < 0) throw new IllegalArgumentException("El offset no puede ser negativo.");
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT ? OFFSET ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNegocio(statement, 1, idNegocio);
            statement.setInt(2, limite);
            statement.setInt(3, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("Error al listar auditorías paginadas: " + SanitizadorLog.error(exception));
        }
        return lista;
    }

    @Override
    public List<Auditoria> listarTodas(long idNegocio) {
        return listarPaginado(idNegocio, 1000, 0);
    }

    private Auditoria mapear(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long idNegocio = rs.getLong("id_negocio");
        String accionStr = rs.getString("accion");
        AccionAuditoria accion = AccionAuditoria.CREAR;
        try {
            if (accionStr != null) {
                accion = AccionAuditoria.valueOf(accionStr.trim().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            // Fallback si la acción no coincide exactamente con el enum
        }

        String entidad = rs.getString("entidad");
        int idEntidad = rs.getInt("id_entidad");
        String usuario = rs.getString("usuario");
        String ipOrigen = rs.getString("ip_origen");
        String userAgent = rs.getString("user_agent");
        String resultado = rs.getString("resultado");
        String descripcion = rs.getString("descripcion");
        String detalle = rs.getString("detalle");

        Timestamp timestamp = rs.getTimestamp("fecha");
        LocalDateTime fecha = timestamp != null ? timestamp.toLocalDateTime() : LocalDateTime.now();

        Auditoria auditoria = new Auditoria(id, accion, entidad, idEntidad, usuario, ipOrigen,
                userAgent, resultado, descripcion, detalle, fecha);
        auditoria.setIdNegocio(idNegocio);
        return auditoria;
    }
}
