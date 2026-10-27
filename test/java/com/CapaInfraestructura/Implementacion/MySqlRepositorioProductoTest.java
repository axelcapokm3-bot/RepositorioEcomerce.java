package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioProductoTest {

    private final MySqlRepositorioProducto repositorio = new MySqlRepositorioProducto();

    @Test
    void eliminarFallaCuandoNoAfectaFilas() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> repositorio.eliminar(3L, 7, connection));

        assertEquals("No se encontró Producto con id 7.", error.getMessage());
        verify(statement).executeUpdate();
    }

    @Test
    void eliminarAceptaUnaFilaAfectada() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        assertDoesNotThrow(() -> repositorio.eliminar(3L, 7, connection));
    }

    @Test
    void listarPaginadoDelegaElRecorteEnMySQL() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, true, false);
        when(result.getInt("id")).thenReturn(7, 8);
        when(result.getString("nombre")).thenReturn("Teclado");
        when(result.getBigDecimal("precio")).thenReturn(new BigDecimal("99.99"));
        when(result.getInt("stock_fisico")).thenReturn(5);
        when(result.getInt("stock_reservado")).thenReturn(0);
        when(result.getLong("id_negocio")).thenReturn(3L);
        when(result.getBoolean("activo")).thenReturn(true);
        when(result.getString("categoria")).thenReturn("ELECTRONICA");

        List<Producto> productos = new MySqlRepositorioProducto(() -> connection).listarPaginado(3L, 2, 4);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().endsWith("ORDER BY p.id LIMIT ? OFFSET ?"),
                "El listado debe paginar con LIMIT ? OFFSET ?: " + sql.getValue());
        verify(statement).setLong(1, 3L);
        verify(statement).setInt(2, 2);
        verify(statement).setInt(3, 4);
        assertEquals(2, productos.size());
        assertEquals(7, productos.get(0).getId());
        assertEquals(8, productos.get(1).getId());
    }
}
