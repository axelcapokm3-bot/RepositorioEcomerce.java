package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolutorConfiguracionTest {

    @Test
    void sistemaPisaEntornoYArchivo() {
        ResolutorConfiguracion resolutor = crear(
                Map.of("stripe.secret-key", "sistema"),
                Map.of("STRIPE_SECRET_KEY", "entorno"),
                Map.of("stripe.secret-key", "archivo"));

        assertEquals("sistema", resolutor.obtener("stripe.secret-key"));
    }

    @Test
    void entornoPisaArchivo() {
        ResolutorConfiguracion resolutor = crear(
                Map.of(),
                Map.of("STRIPE_SECRET_KEY", "entorno"),
                Map.of("stripe.secret-key", "archivo"));

        assertEquals("entorno", resolutor.obtener("stripe.secret-key"));
    }

    @Test
    void archivoEsElUltimoFallback() {
        ResolutorConfiguracion resolutor = crear(
                Map.of(),
                Map.of(),
                Map.of("stripe.secret-key", "archivo"));

        assertEquals("archivo", resolutor.obtener("stripe.secret-key"));
    }

    @Test
    void ignoraValoresVaciosYContinuaConLaSiguienteFuente() {
        ResolutorConfiguracion resolutor = crear(
                Map.of("stripe.secret-key", " "),
                Map.of("STRIPE_SECRET_KEY", "entorno"),
                Map.of("stripe.secret-key", "archivo"));

        assertEquals("entorno", resolutor.obtener("stripe.secret-key"));
    }

    @Test
    void fallaEnProduccionSiExisteConfiguracionLocal(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("config.properties");
        Files.createFile(archivo);
        ResolutorConfiguracion resolutor = crearConArchivo(
                Map.of(),
                Map.of("ENTORNO", "prod"),
                Map.of(),
                archivo);

        IllegalStateException error = assertThrows(
                IllegalStateException.class, resolutor::validarArranque);
        assertTrue(error.getMessage().contains("config.properties"));
    }

    @Test
    void fallaEnProduccionConAppPerfilSiNoHayEntorno(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("config.properties");
        Files.createFile(archivo);
        ResolutorConfiguracion resolutor = crearConArchivo(
                Map.of(),
                Map.of("APP_PERFIL", "prod"),
                Map.of(),
                archivo);

        assertThrows(IllegalStateException.class, resolutor::validarArranque);
    }

    @Test
    void noFallaEnDesarrolloAunqueExistaConfiguracionLocal(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("config.properties");
        Files.createFile(archivo);
        ResolutorConfiguracion resolutor = crearConArchivo(
                Map.of(),
                Map.of("ENTORNO", "dev"),
                Map.of(),
                archivo);

        assertDoesNotThrow(resolutor::validarArranque);
    }

    @Test
    void noFallaEnProduccionSiNoExisteConfiguracionLocal(@TempDir Path tempDir) {
        ResolutorConfiguracion resolutor = crearConArchivo(
                Map.of(),
                Map.of("ENTORNO", "prod"),
                Map.of(),
                tempDir.resolve("config.properties"));

        assertDoesNotThrow(resolutor::validarArranque);
    }

    private static ResolutorConfiguracion crear(
            Map<String, String> sistema,
            Map<String, String> entorno,
            Map<String, String> archivo) {
        return crearConArchivo(sistema, entorno, archivo, Path.of("config.properties.prueba"));
    }

    private static ResolutorConfiguracion crearConArchivo(
            Map<String, String> sistema,
            Map<String, String> entorno,
            Map<String, String> archivo,
            Path archivoLocal) {
        return ResolutorConfiguracion.paraPruebas(
                fuente(sistema), fuente(entorno), fuente(archivo), archivoLocal);
    }

    private static Function<String, String> fuente(Map<String, String> valores) {
        return valores::get;
    }
}
