package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaDominio.Entidades.EstadoEventoWebhook;
import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Entidades.ProveedorWebhook;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioEventoWebhookTest {

    @Test
    void registraConInsertIgnoreYDevuelveSiEsNuevo() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        MySqlRepositorioEventoWebhook repositorio = new MySqlRepositorioEventoWebhook(() -> connection);

        boolean nuevo = repositorio.registrar(new EventoWebhook(
                ProveedorWebhook.MERCADO_PAGO, "payment", "123", "{}"));

        assertTrue(nuevo);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().startsWith("INSERT IGNORE INTO eventos_webhook"));
        verify(statement).setString(1, "MERCADO_PAGO");
        verify(statement).setString(2, "payment");
        verify(statement).setString(3, "123");
        verify(statement).setString(4, "{}");
    }

    @Test
    void devuelveFalseCuandoElEventoYaExiste() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        MySqlRepositorioEventoWebhook repositorio = new MySqlRepositorioEventoWebhook(() -> connection);

        assertFalse(repositorio.registrar(new EventoWebhook(
                ProveedorWebhook.STRIPE, "checkout.session.completed", "evt_1", "{}")));
    }

    @Test
    void listaPendientesYReconstruyeElEvento() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("id")).thenReturn(4);
        when(rs.getString("proveedor")).thenReturn("STRIPE");
        when(rs.getString("tipo")).thenReturn("checkout.session.completed");
        when(rs.getString("evento_id")).thenReturn("evt_4");
        when(rs.getString("payload")).thenReturn("{\"id\":\"evt_4\"}");
        when(rs.getString("estado")).thenReturn("PENDIENTE");
        when(rs.getInt("intentos")).thenReturn(2);
        when(rs.getString("ultimo_error")).thenReturn("temporal");
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        when(rs.getTimestamp("fecha_recepcion")).thenReturn(ahora);
        when(rs.getTimestamp("fecha_actualizacion")).thenReturn(ahora);
        when(rs.getTimestamp("fecha_procesamiento")).thenReturn(null);
        MySqlRepositorioEventoWebhook repositorio = new MySqlRepositorioEventoWebhook(() -> connection);

        List<EventoWebhook> eventos = repositorio.listarPendientes(10);

        assertEquals(1, eventos.size());
        EventoWebhook evento = eventos.get(0);
        assertEquals(4, evento.getId());
        assertEquals(ProveedorWebhook.STRIPE, evento.getProveedor());
        assertEquals(EstadoEventoWebhook.PENDIENTE, evento.getEstado());
        assertEquals(2, evento.getIntentos());
        assertEquals("temporal", evento.getUltimoError());
        verify(statement).setString(1, "PENDIENTE");
        verify(statement).setInt(2, 10);
    }

    @Test
    void marcaProcesandoSoloSiSiguePendienteEIncrementaIntentos() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        MySqlRepositorioEventoWebhook repositorio = new MySqlRepositorioEventoWebhook(() -> connection);

        assertTrue(repositorio.marcarProcesando(9));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("intentos = intentos + 1"));
        assertTrue(sql.getValue().contains("WHERE id = ? AND estado = ?"));
        verify(statement).setString(1, "PROCESANDO");
        verify(statement).setInt(3, 9);
        verify(statement).setString(4, "PENDIENTE");
    }
}
