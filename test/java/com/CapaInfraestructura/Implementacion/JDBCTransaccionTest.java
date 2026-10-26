package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;

import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;

class JDBCTransaccionTest {

    @Test
    void conservaLaCausaOriginalCuandoFallaElRollback() throws Exception {
        Connection conexion = mock(Connection.class);
        SQLException errorRollback = new SQLException("rollback fallido");
        doThrow(errorRollback).when(conexion).rollback();
        IllegalStateException causaOriginal = new IllegalStateException("fallo original");
        JDBCTransaccion transaccion = new JDBCTransaccion(() -> conexion);

        RuntimeException lanzada = assertThrows(RuntimeException.class,
                () -> transaccion.ejecutarTransaccion(conexionTransaccional -> {
                    throw causaOriginal;
                }));

        assertSame(causaOriginal, lanzada.getCause());
        assertEquals(1, causaOriginal.getSuppressed().length);
        assertSame(errorRollback, causaOriginal.getSuppressed()[0]);
        verify(conexion).setAutoCommit(false);
        verify(conexion).rollback();
        verify(conexion, never()).commit();
    }

    @Test
    void conservaLaExcepcionDeNegocioYAgregaElFalloDeRollbackComoSuprimido() throws Exception {
        Connection conexion = mock(Connection.class);
        SQLException errorRollback = new SQLException("rollback fallido");
        doThrow(errorRollback).when(conexion).rollback();
        RecursoNoEncontradoException causaOriginal = new RecursoNoEncontradoException("No existe.");
        JDBCTransaccion transaccion = new JDBCTransaccion(() -> conexion);

        RecursoNoEncontradoException lanzada = assertThrows(RecursoNoEncontradoException.class,
                () -> transaccion.ejecutarTransaccion(conexionTransaccional -> {
                    throw causaOriginal;
                }));

        assertSame(causaOriginal, lanzada);
        assertEquals(1, lanzada.getSuppressed().length);
        assertSame(errorRollback, lanzada.getSuppressed()[0]);
    }

    @Test
    void haceCommitCuandoLaOperacionTerminaBien() throws Exception {
        Connection conexion = mock(Connection.class);
        JDBCTransaccion transaccion = new JDBCTransaccion(() -> conexion);

        assertDoesNotThrow(() -> transaccion.ejecutarTransaccion(conexionTransaccional -> {
        }));

        verify(conexion).setAutoCommit(false);
        verify(conexion).commit();
        verify(conexion, never()).rollback();
    }
}
