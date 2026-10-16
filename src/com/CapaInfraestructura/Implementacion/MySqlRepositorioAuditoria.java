package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio de auditoría persistente en MySQL utilizando JDBC y HikariCP.
 * Sigue estrictamente el principio Append-Only.
 */
public class MySqlRepositorioAuditoria implements IAuditoriaRepository {

    private static final String INSERT = "INSERT INTO auditoria "
            + "(accion, entidad, id_entidad, usuario, ip_origen, user_agent, resultado, descripcion, detalle) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BASE = "SELECT a.id, a.accion, a.entidad, a.id_entidad, a.usuario, a.ip_origen, "
            + "a.user_agent, a.resultado, a.descripcion, a.detalle, a.fecha FROM auditoria a ";

    private static final String FILTRO_NEGOCIO =
            "((a.entidad = 'Venta' AND EXISTS (SELECT 1 FROM ventas v WHERE v.id = a.id_entidad AND v.id_negocio = ?)) "
            + "OR (a.entidad = 'Producto' AND EXISTS (SELECT 1 FROM productos p WHERE p.id = a.id_entidad AND p.id_negocio = ?)) "
            + "OR (a.entidad = 'Cliente' AND EXISTS (SELECT 1 FROM clientes c WHERE c.id = a.id_entidad AND c.id_negocio = ?)) "
            + "OR (a.entidad = 'Pago' AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id = a.id_entidad AND pg.id_negocio = ?)))";

    private static void bindNegocio(PreparedStatement statement, int start, long idNegocio) throws SQLException {
        for (int i = 0; i < 4; i++) {
            statement.setLong(start + i, idNegocio);
        }
    }

    @Override
    public void guardar(Auditoria auditoria) throws SQLException {
        if (auditoria == null) {
            throw new IllegalArgumentException("El registro de auditoría no puede ser nulo.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            
            statement.setString(1, auditoria.getAccion() != null ? auditoria.getAccion().name() : AccionAuditoria.CREAR.name());
            statement.setString(2, auditoria.getEntidad() != null ? auditoria.getEntidad() : "Venta");
            statement.setInt(3, auditoria.getIdEntidad());
            statement.setString(4, auditoria.getUsuario());
            statement.setString(5, auditoria.getIpOrigen());
            statement.setString(6, auditoria.getUserAgent());
            statement.setString(7, auditoria.getResultado() != null ? auditoria.getResultado() : "OK");
            statement.setString(8, auditoria.getDescripcion() != null ? auditoria.getDescripcion() : "");
            statement.setString(9, auditoria.getDetalle());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    auditoria.setId(keys.getLong(1));
                }
            }
        } catch (SQLException exception) {
            System.err.println("❌ Error al guardar auditoría en MySQL: " + exception.getMessage());
            throw exception;
        }
    }

    @Override
    public List<Auditoria> buscarPorEntidad(long idNegocio, String entidad, int idEntidad) {
        List<Auditoria> lista = new ArrayList<>();
        String filtro = filtroEntidad(entidad);
        if (filtro == null) {
            return lista;
        }
        String sql = SELECT_BASE + "WHERE a.entidad = ? AND a.id_entidad = ? AND " + filtro
                + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
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
            System.err.println("❌ Error al buscar auditoría por entidad: " + exception.getMessage());
        }
        return lista;
    }

    private static String filtroEntidad(String entidad) {
        if ("Venta".equalsIgnoreCase(entidad)) {
            return "EXISTS (SELECT 1 FROM ventas v WHERE v.id = a.id_entidad AND v.id_negocio = ?)";
        }
        if ("Producto".equalsIgnoreCase(entidad)) {
            return "EXISTS (SELECT 1 FROM productos p WHERE p.id = a.id_entidad AND p.id_negocio = ?)";
        }
        if ("Cliente".equalsIgnoreCase(entidad)) {
            return "EXISTS (SELECT 1 FROM clientes c WHERE c.id = a.id_entidad AND c.id_negocio = ?)";
        }
        if ("Pago".equalsIgnoreCase(entidad)) {
            return "EXISTS (SELECT 1 FROM pagos pg WHERE pg.id = a.id_entidad AND pg.id_negocio = ?)";
        }
        return null;
    }

    @Override
    public List<Auditoria> buscarPorUsuario(long idNegocio, String usuario) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.usuario = ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usuario);
            bindNegocio(statement, 2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("❌ Error al buscar auditoría por usuario: " + exception.getMessage());
        }
        return lista;
    }

    @Override
    public List<Auditoria> buscarPorAccion(long idNegocio, AccionAuditoria accion) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.accion = ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accion.name());
            bindNegocio(statement, 2, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("❌ Error al buscar auditoría por acción: " + exception.getMessage());
        }
        return lista;
    }

    @Override
    public List<Auditoria> buscarPorRangoFecha(long idNegocio, LocalDateTime desde, LocalDateTime hasta) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE a.fecha BETWEEN ? AND ? AND " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 1000";
        try (Connection connection = DatabaseConnection.getConnection();
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
            System.err.println("❌ Error al buscar auditoría por rango de fecha: " + exception.getMessage());
        }
        return lista;
    }

    @Override
    public List<Auditoria> listarTodas(long idNegocio) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE " + FILTRO_NEGOCIO + " ORDER BY a.fecha DESC LIMIT 1000";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNegocio(statement, 1, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException exception) {
            System.err.println("❌ Error al listar todas las auditorías: " + exception.getMessage());
        }
        return lista;
    }

    private Auditoria mapear(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
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

        return new Auditoria(id, accion, entidad, idEntidad, usuario, ipOrigen, userAgent, resultado, descripcion, detalle, fecha);
    }
}
