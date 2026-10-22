package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
