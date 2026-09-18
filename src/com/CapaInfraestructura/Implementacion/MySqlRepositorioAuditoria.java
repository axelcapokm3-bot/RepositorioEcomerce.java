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

    private static final String SELECT_BASE = "SELECT id, accion, entidad, id_entidad, usuario, ip_origen, "
            + "user_agent, resultado, descripcion, detalle, fecha FROM auditoria ";

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
    public List<Auditoria> buscarPorEntidad(String entidad, int idEntidad) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE entidad = ? AND id_entidad = ? ORDER BY fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entidad);
            statement.setInt(2, idEntidad);
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

    @Override
    public List<Auditoria> buscarPorUsuario(String usuario) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE usuario = ? ORDER BY fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usuario);
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
    public List<Auditoria> buscarPorAccion(AccionAuditoria accion) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE accion = ? ORDER BY fecha DESC LIMIT 500";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accion.name());
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
    public List<Auditoria> buscarPorRangoFecha(LocalDateTime desde, LocalDateTime hasta) {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE fecha BETWEEN ? AND ? ORDER BY fecha DESC LIMIT 1000";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, desde != null ? Timestamp.valueOf(desde) : Timestamp.valueOf(LocalDateTime.now().minusYears(1)));
            statement.setTimestamp(2, hasta != null ? Timestamp.valueOf(hasta) : Timestamp.valueOf(LocalDateTime.now()));
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
    public List<Auditoria> listarTodas() {
        List<Auditoria> lista = new ArrayList<>();
        String sql = SELECT_BASE + "ORDER BY fecha DESC LIMIT 1000";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
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
