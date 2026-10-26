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

import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;

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
}
