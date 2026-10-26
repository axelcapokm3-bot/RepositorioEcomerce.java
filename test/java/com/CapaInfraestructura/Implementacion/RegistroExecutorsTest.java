package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistroExecutorsTest {

    @Test
    @DisplayName("acepta la configuración predeterminada al arrancar")
    void aceptaConfiguracionPredeterminada() {
        assertDoesNotThrow(RegistroExecutors::validarConfiguracion);
    }

    @Test
    @DisplayName("falla al arrancar si una configuración de executor es inválida")
    void fallaConConfiguracionInvalida() {
        System.setProperty("executor.venta.core", "-1");
        try {
            assertThrows(IllegalStateException.class, RegistroExecutors::validarConfiguracion);
        } finally {
            System.clearProperty("executor.venta.core");
        }
    }
}
