package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.List;

import com.CapaDominio.Entidades.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioVentaTest {

    @Test
    @DisplayName("detecta el duplicado del índice de idempotencia")
    void detectaDuplicadoDeIdempotencia() {
        SQLIntegrityConstraintViolationException duplicado =
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1-clave' for key 'ventas.uq_ventas_negocio_idempotencia'");

        assertTrue(MySqlRepositorioVenta.esDuplicadoDeIdempotencia(duplicado));
    }

    @Test
    @DisplayName("no confunde otros duplicados con idempotencia")
    void ignoraOtrosDuplicados() {
        SQLIntegrityConstraintViolationException duplicado =
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1' for key 'ventas.PRIMARY'");

        assertFalse(MySqlRepositorioVenta.esDuplicadoDeIdempotencia(duplicado));
        assertFalse(MySqlRepositorioVenta.esDuplicadoDeIdempotencia(
                new SQLException("Error de conexión")));
        assertFalse(MySqlRepositorioVenta.esDuplicadoDeIdempotencia(null));
    }

    @Test
    @DisplayName("detecta el duplicado envuelto en la cadena de SQLException")
    void detectaDuplicadoEncadenado() {
        SQLException principal = new SQLException("Error al insertar");
        principal.setNextException(new SQLIntegrityConstraintViolationException(
                "Duplicate entry '1-clave' for key 'uq_ventas_clave_idempotencia'"));

        assertTrue(MySqlRepositorioVenta.esDuplicadoDeIdempotencia(principal));
    }

    @Test
    @DisplayName("listarPaginado pagina en MySQL y carga detalles con UNA sola consulta IN (sin N+1)")
    void listarPaginadoEjecutaDosConsultasYAgrupaDetalles() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement stmtVentas = mock(PreparedStatement.class);
        PreparedStatement stmtDetalles = mock(PreparedStatement.class);
        ResultSet rsVentas = mock(ResultSet.class);
        ResultSet rsDetalles = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmtVentas, stmtDetalles);
        when(stmtVentas.executeQuery()).thenReturn(rsVentas);
        when(stmtDetalles.executeQuery()).thenReturn(rsDetalles);

        when(rsVentas.next()).thenReturn(true, true, true, false);
        when(rsVentas.getInt("id_cliente")).thenReturn(10);
        when(rsVentas.getInt("id")).thenReturn(1, 2, 3);
        when(rsVentas.getLong("id_negocio")).thenReturn(5L);
        when(rsVentas.getString("tipo_pago")).thenReturn("EFECTIVO");
        when(rsVentas.getString("clave_idempotencia")).thenReturn("clave-1");
        when(rsVentas.getTimestamp("fecha_compra"))
                .thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));
        when(rsVentas.getString("estado")).thenReturn("PENDIENTE");

        when(rsDetalles.next()).thenReturn(true, true, true, true, false);
        when(rsDetalles.getInt("id_venta")).thenReturn(1, 2, 2, 3);
        when(rsDetalles.getInt("id_producto")).thenReturn(100, 200, 201, 300);
        when(rsDetalles.getBigDecimal("precio_unitario")).thenReturn(new BigDecimal("10.00"));
        when(rsDetalles.getInt("cantidad")).thenReturn(1, 1, 2, 1);

        List<Venta> ventas = new MySqlRepositorioVenta(() -> connection).listarPaginado(5L, 50, 10);

        ArgumentCaptor<String> sqls = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sqls.capture());
        assertTrue(sqls.getAllValues().get(0).endsWith("ORDER BY v.id LIMIT ? OFFSET ?"),
                "El listado de ventas debe paginar con LIMIT ? OFFSET ?: " + sqls.getAllValues().get(0));
        assertTrue(sqls.getAllValues().get(1).contains("WHERE id_negocio = ? AND id_venta IN (?, ?, ?)"),
                "Los detalles deben cargarse con un IN dinámico: " + sqls.getAllValues().get(1));

        verify(stmtVentas).setLong(1, 5L);
        verify(stmtVentas).setInt(2, 50);
        verify(stmtVentas).setInt(3, 10);
        verify(stmtDetalles).setLong(1, 5L);
        verify(stmtDetalles).setInt(2, 1);
        verify(stmtDetalles).setInt(3, 2);
        verify(stmtDetalles).setInt(4, 3);

        assertEquals(3, ventas.size());
        assertEquals(1, ventas.get(0).getDetalles().size());
        assertEquals(2, ventas.get(1).getDetalles().size());
        assertEquals(1, ventas.get(2).getDetalles().size());
    }

    @Test
    @DisplayName("listarPaginado sin resultados no consulta detalles")
    void listarPaginadoSinResultadosEjecutaUnaSoloConsulta() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement stmtVentas = mock(PreparedStatement.class);
        ResultSet rsVentas = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmtVentas);
        when(stmtVentas.executeQuery()).thenReturn(rsVentas);
        when(rsVentas.next()).thenReturn(false);

        List<Venta> ventas = new MySqlRepositorioVenta(() -> connection).listarPaginado(5L, 50, 0);

        assertTrue(ventas.isEmpty());
        verify(connection, times(1)).prepareStatement(anyString());
    }

    @Test
    @DisplayName("contarTotal usa SELECT COUNT(*) sin traer filas a memoria")
    void contarTotalEjecutaCount() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getLong(1)).thenReturn(42L);

        assertEquals(42L, new MySqlRepositorioVenta(() -> connection).contarTotal(5L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("SELECT COUNT(*) FROM ventas"));
        assertFalse(sql.getValue().contains("SELECT v."));
    }
}
