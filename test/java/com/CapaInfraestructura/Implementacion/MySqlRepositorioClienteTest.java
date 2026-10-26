package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;

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

    private static Cliente cliente() {
        Cliente cliente = new Cliente(7, "30111222", "Ana", "Pérez",
                "Calle 1", LocalDate.of(1990, 1, 1), "CABA");
        cliente.setIdNegocio(3L);
        return cliente;
    }
}
