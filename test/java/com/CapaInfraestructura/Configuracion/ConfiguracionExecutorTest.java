package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.Interfaces.TipoServicio;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfiguracionExecutorTest {

    @Test
    @DisplayName("usa los tamaños predeterminados de cada tipo de servicio")
    void valoresPredeterminados() {
        afirmarPredeterminada(TipoServicio.VENTA, 5, 10, 100);
        afirmarPredeterminada(TipoServicio.PRODUCTO, 2, 5, 50);
        afirmarPredeterminada(TipoServicio.CLIENTE, 2, 2, 30);
        afirmarPredeterminada(TipoServicio.AUDITORIA, 1, 2, 20);
        afirmarPredeterminada(TipoServicio.NOTIFICACIONES, 1, 1, 50);
        afirmarPredeterminada(TipoServicio.AUTENTICACION, 1, 2, 50);
    }

    @Test
    @DisplayName("lee core, max y cola desde la configuración")
    void leeSobrescrituras() {
        Map<String, String> propiedades = Map.of(
                "executor.venta.core", "7",
                "executor.venta.max", "12",
                "executor.venta.cola", "150");

        ConfiguracionExecutor configuracion =
                ConfiguracionExecutor.para(TipoServicio.VENTA, propiedades::get);

        assertAll(
                () -> assertEquals(7, configuracion.tamanoCore()),
                () -> assertEquals(12, configuracion.tamanoMax()),
                () -> assertEquals(150, configuracion.capacidadCola()));
    }

    @Test
    @DisplayName("permite sobrescribir solo una clave y conserva el resto")
    void conservaPredeterminadosNoConfigurados() {
        ConfiguracionExecutor configuracion = ConfiguracionExecutor.para(
                TipoServicio.NOTIFICACIONES,
                Map.of("executor.notificaciones.max", "3")::get);

        assertAll(
                () -> assertEquals(1, configuracion.tamanoCore()),
                () -> assertEquals(3, configuracion.tamanoMax()),
                () -> assertEquals(50, configuracion.capacidadCola()));
    }

    @Test
    @DisplayName("rechaza valores no numéricos con la clave configurada")
    void rechazaValorNoNumerico() {
        afirmarError(Map.of("executor.venta.core", "cinco"), "executor.venta.core");
    }

    @Test
    @DisplayName("rechaza core negativo")
    void rechazaCoreNegativo() {
        afirmarError(Map.of("executor.venta.core", "-1"), "executor.venta.core");
    }

    @Test
    @DisplayName("rechaza max menor que uno")
    void rechazaMaxInvalido() {
        afirmarError(Map.of("executor.venta.max", "0"), "executor.venta.max");
    }

    @Test
    @DisplayName("rechaza core mayor que max")
    void rechazaCoreMayorQueMax() {
        afirmarError(Map.of(
                "executor.venta.core", "8",
                "executor.venta.max", "4"), "executor.venta.core");
    }

    @Test
    @DisplayName("rechaza cola menor que uno")
    void rechazaColaInvalida() {
        afirmarError(Map.of("executor.venta.cola", "0"), "executor.venta.cola");
    }

    @Test
    @DisplayName("rechaza un tipo de servicio nulo")
    void rechazaTipoNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguracionExecutor.para(null, clave -> null));
    }

    private static void afirmarPredeterminada(TipoServicio tipo, int core, int max, int cola) {
        ConfiguracionExecutor configuracion =
                ConfiguracionExecutor.para(tipo, clave -> null);
        assertAll(
                () -> assertEquals(core, configuracion.tamanoCore()),
                () -> assertEquals(max, configuracion.tamanoMax()),
                () -> assertEquals(cola, configuracion.capacidadCola()));
    }

    private static void afirmarError(Map<String, String> propiedades, String fragmento) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ConfiguracionExecutor.para(TipoServicio.VENTA, propiedades::get));
        assertTrue(error.getMessage().contains(fragmento), error.getMessage());
    }
}
