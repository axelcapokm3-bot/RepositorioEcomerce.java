package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IEventoWebhookRepository;
import com.CapaDominio.Entidades.EstadoEventoWebhook;
import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Entidades.ProveedorWebhook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MySqlRepositorioEventoWebhook implements IEventoWebhookRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private static final String COLUMNAS = "id, proveedor, tipo, evento_id, payload, estado, intentos, "
            + "ultimo_error, fecha_recepcion, fecha_actualizacion, fecha_procesamiento";
    private final ConnectionProvider connectionProvider;

    public MySqlRepositorioEventoWebhook() {
        this(DatabaseConnection::getConnection);
    }

    MySqlRepositorioEventoWebhook(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public boolean registrar(EventoWebhook evento) {
        if (evento == null) throw new IllegalArgumentException("El evento de webhook no puede ser nulo.");
        if (evento.getProveedor() == null) {
            throw new IllegalArgumentException("El proveedor del webhook es obligatorio.");
        }
        String sql = "INSERT IGNORE INTO eventos_webhook "
                + "(proveedor, tipo, evento_id, payload, estado, intentos, fecha_recepcion) "
                + "VALUES (?, ?, ?, ?, ?, 0, ?)";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, evento.getProveedor().name());
            statement.setString(2, evento.getTipo());
            statement.setString(3, evento.getEventoId());
            statement.setString(4, evento.getPayload());
            statement.setString(5, EstadoEventoWebhook.PENDIENTE.name());
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al registrar el evento de webhook: " + exception.getMessage(), exception);
        }
    }

    @Override
    public Optional<EventoWebhook> buscarPorId(int id) {
        if (id <= 0) throw new IllegalArgumentException("El ID del evento debe ser positivo.");
        String sql = "SELECT " + COLUMNAS + " FROM eventos_webhook WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return Optional.of(mapEvento(result));
            }
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al buscar el evento de webhook: " + exception.getMessage(), exception);
        }
        return Optional.empty();
    }

    @Override
    public List<EventoWebhook> listarPendientes(int limite) {
        if (limite <= 0) throw new IllegalArgumentException("El límite debe ser positivo.");
        String sql = "SELECT " + COLUMNAS + " FROM eventos_webhook "
                + "WHERE estado = ? ORDER BY fecha_recepcion, id LIMIT ?";
        List<EventoWebhook> eventos = new ArrayList<>();
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.PENDIENTE.name());
            statement.setInt(2, limite);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) eventos.add(mapEvento(result));
            }
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al listar eventos de webhook pendientes: " + exception.getMessage(), exception);
        }
        return eventos;
    }

    @Override
    public boolean marcarProcesando(int id) {
        String sql = "UPDATE eventos_webhook SET estado = ?, intentos = intentos + 1, "
                + "fecha_actualizacion = ? WHERE id = ? AND estado = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.PROCESANDO.name());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(3, id);
            statement.setString(4, EstadoEventoWebhook.PENDIENTE.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al marcar el webhook como procesando: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void marcarProcesado(int id) {
        String sql = "UPDATE eventos_webhook SET estado = ?, ultimo_error = NULL, "
                + "fecha_procesamiento = ?, fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.PROCESADO.name());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(4, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al marcar el webhook como procesado: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void marcarReintento(int id, String error) {
        String sql = "UPDATE eventos_webhook SET estado = ?, ultimo_error = ?, "
                + "fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.PENDIENTE.name());
            statement.setString(2, error);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(4, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al reprogramar el webhook: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void marcarFallido(int id, String error) {
        String sql = "UPDATE eventos_webhook SET estado = ?, ultimo_error = ?, "
                + "fecha_procesamiento = ?, fecha_actualizacion = ? WHERE id = ?";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.FALLIDO.name());
            statement.setString(2, error);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(5, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al marcar el webhook como fallido: " + exception.getMessage(), exception);
        }
    }

    @Override
    public int recuperarProcesandoAntiguos(int minutos) {
        if (minutos <= 0) throw new IllegalArgumentException("Los minutos deben ser positivos.");
        String sql = "UPDATE eventos_webhook SET estado = ?, fecha_actualizacion = ? "
                + "WHERE estado = ? AND fecha_actualizacion < TIMESTAMPADD(MINUTE, ?, NOW())";
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EstadoEventoWebhook.PENDIENTE.name());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, EstadoEventoWebhook.PROCESANDO.name());
            statement.setInt(4, -minutos);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Error al recuperar webhooks en proceso: " + exception.getMessage(), exception);
        }
    }

    private EventoWebhook mapEvento(ResultSet result) throws SQLException {
        Timestamp recepcion = result.getTimestamp("fecha_recepcion");
        Timestamp actualizacion = result.getTimestamp("fecha_actualizacion");
        Timestamp procesamiento = result.getTimestamp("fecha_procesamiento");
        return new EventoWebhook(
                result.getInt("id"),
                ProveedorWebhook.valueOf(result.getString("proveedor")),
                result.getString("tipo"),
                result.getString("evento_id"),
                result.getString("payload"),
                EstadoEventoWebhook.valueOf(result.getString("estado")),
                result.getInt("intentos"),
                result.getString("ultimo_error"),
                recepcion == null ? null : recepcion.toLocalDateTime(),
                actualizacion == null ? null : actualizacion.toLocalDateTime(),
                procesamiento == null ? null : procesamiento.toLocalDateTime());
    }
}
