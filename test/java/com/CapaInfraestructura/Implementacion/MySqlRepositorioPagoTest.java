package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.TipoDePago;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioPagoTest {

    @Test
    void buscaCandidatosMercadoPagoPorVentaValidandoElNegocioEnLaConsulta() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("id")).thenReturn(10);
        when(rs.getInt("id_venta")).thenReturn(5);
        when(rs.getString("pasarela")).thenReturn(TipoDePago.MERCADO_PAGO.name());
        when(rs.getString("estado")).thenReturn(EstadoPago.PENDIENTE.name());
        when(rs.getBigDecimal("monto")).thenReturn(new BigDecimal("100.00"));
        when(rs.getString("moneda")).thenReturn("ARS");
        when(rs.getLong("id_negocio")).thenReturn(7L);

        MySqlRepositorioPago repositorio = new MySqlRepositorioPago(() -> connection);
        List<Pago> pagos = repositorio.buscarCandidatosMercadoPagoPorVenta(5);

        assertEquals(1, pagos.size());
        assertEquals(10, pagos.get(0).getId());
        assertEquals(5, pagos.get(0).getIdVenta());
        assertEquals(7L, pagos.get(0).getIdNegocio());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().startsWith("SELECT p.id, p.id_venta"));
        assertTrue(sql.getValue().contains(
                "JOIN ventas v ON v.id = p.id_venta AND v.id_negocio = p.id_negocio"));
        assertTrue(sql.getValue().contains("UPPER(p.pasarela) = ?"));
        verify(statement).setInt(1, 5);
        verify(statement).setString(2, TipoDePago.MERCADO_PAGO.name());
    }

    @Test
    void rechazaIdentificadoresInvalidosAntesDeConsultar() {
        MySqlRepositorioPago repositorio = new MySqlRepositorioPago(
                () -> {
                    throw new AssertionError("No debe abrir una conexión");
                });

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> repositorio.buscarCandidatosMercadoPagoPorVenta(0));
    }
}
