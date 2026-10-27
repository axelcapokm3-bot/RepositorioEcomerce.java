package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlRepositorioClienteTest {

    @Test
    void actualizarFallaCuandoNoAfectaFilas() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        MySqlRepositorioCliente repositorio = new MySqlRepositorioCliente(() -> connection);

        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> repositorio.actualizar(3L, 7, cliente()));

        assertEquals("No se encontró Cliente con id 7.", error.getMessage());
        verify(statement).executeUpdate();
    }

    @Test
    void actualizarAceptaUnaFilaAfectada() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        MySqlRepositorioCliente repositorio = new MySqlRepositorioCliente(() -> connection);

        assertDoesNotThrow(() -> repositorio.actualizar(3L, 7, cliente()));
    }

    @Test
    void eliminarFallaCuandoNoAfectaFilas() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        MySqlRepositorioCliente repositorio = new MySqlRepositorioCliente(() -> connection);

        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> repositorio.eliminar(3L, 7));

        assertEquals("No se encontró Cliente con id 7.", error.getMessage());
    }

    @Test
    void eliminarAceptaUnaFilaAfectada() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        MySqlRepositorioCliente repositorio = new MySqlRepositorioCliente(() -> connection);

        assertDoesNotThrow(() -> repositorio.eliminar(3L, 7));
    }

    @Test
    void listarPaginadoDelegaElRecorteEnMySQL() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getInt("id")).thenReturn(7);
        when(result.getString("dni")).thenReturn("30111222");
        when(result.getString("nombre")).thenReturn("Ana");
        when(result.getString("apellido")).thenReturn("Pérez");
        when(result.getString("direccion")).thenReturn("Calle 1");
        when(result.getDate("fecha_nacimiento")).thenReturn(Date.valueOf("1990-01-01"));
        when(result.getString("localidad")).thenReturn("CABA");
        when(result.getBoolean("activo")).thenReturn(true);
        when(result.getLong("id_negocio")).thenReturn(3L);

        MySqlRepositorioCliente repositorio = new MySqlRepositorioCliente(() -> connection);
        List<Cliente> clientes = repositorio.listarPaginado(3L, 1, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().endsWith("ORDER BY id LIMIT ? OFFSET ?"),
                "El listado debe paginar con LIMIT ? OFFSET ?: " + sql.getValue());
        verify(statement).setLong(1, 3L);
        verify(statement).setInt(2, 1);
        verify(statement).setInt(3, 20);
        assertEquals(1, clientes.size());
        assertEquals(7, clientes.get(0).getId());
    }

    private static Cliente cliente() {
        Cliente cliente = new Cliente(7, "30111222", "Ana", "Pérez",
                "Calle 1", LocalDate.of(1990, 1, 1), "CABA");
        cliente.setIdNegocio(3L);
        return cliente;
    }
}
