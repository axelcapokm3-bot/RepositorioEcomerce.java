package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import com.CapaDominio.Entidades.Auditoria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioAuditoriaTest {

    @Test
    void listarPaginadoDelegaElRecorteEnMySQL() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, true, false);
        when(result.getLong("id")).thenReturn(1L, 2L);
        when(result.getLong("id_negocio")).thenReturn(3L);
        when(result.getString("accion")).thenReturn("CREAR");
        when(result.getString("entidad")).thenReturn("Venta");
        when(result.getInt("id_entidad")).thenReturn(9);
        when(result.getString("usuario")).thenReturn("admin");
        when(result.getString("ip_origen")).thenReturn("127.0.0.1");
        when(result.getString("user_agent")).thenReturn("JUnit");
        when(result.getString("resultado")).thenReturn("OK");
        when(result.getString("descripcion")).thenReturn("alta");
        when(result.getString("detalle")).thenReturn("{}");
        when(result.getTimestamp("fecha")).thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));

        List<Auditoria> registros = new MySqlRepositorioAuditoria(() -> connection).listarPaginado(3L, 25, 75);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().endsWith("ORDER BY a.fecha DESC LIMIT ? OFFSET ?"),
                "El listado debe paginar con LIMIT ? OFFSET ?: " + sql.getValue());
        verify(statement).setLong(1, 3L);
        verify(statement).setInt(2, 25);
        verify(statement).setInt(3, 75);
        assertEquals(2, registros.size());
        assertEquals(1L, registros.get(0).getId());
        assertEquals(2L, registros.get(1).getId());
    }

    @Test
    void contarTotalEjecutaCountSinLimit() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getLong(1)).thenReturn(42L);

        assertEquals(42L, new MySqlRepositorioAuditoria(() -> connection).contarTotal(3L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("SELECT COUNT(*) FROM auditoria"));
        assertFalse(sql.getValue().contains("LIMIT"));
    }
}
