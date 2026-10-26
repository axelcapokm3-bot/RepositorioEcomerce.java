package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

    @Test
    @DisplayName("mapea las claves de executors a variables de entorno")
    void mapeaExecutorAVariableDeEntorno() {
        assertEquals("EXECUTOR_VENTA_CORE",
                ConfigLoader.obtenerNombreVariableEntorno("executor.venta.core"));
        assertEquals("EXECUTOR_NOTIFICACIONES_COLA",
                ConfigLoader.obtenerNombreVariableEntorno("executor.notificaciones.cola"));
    }
}
