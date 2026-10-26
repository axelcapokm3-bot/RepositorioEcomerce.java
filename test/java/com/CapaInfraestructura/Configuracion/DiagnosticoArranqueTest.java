package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DiagnosticoArranqueTest {

    @Test
    void emiteEventoComponenteYErrorEnUnaSolaLinea() {
        String linea = DiagnosticoArranque.linea("startup.fail", "DatabaseConnection",
                new IllegalStateException("No se pudo conectar"));

        assertEquals("event=startup.fail component=DatabaseConnection "
                + "error=IllegalStateException: No se pudo conectar", linea);
    }

    @Test
    void saneaCredencialesYSaltosDeLinea() {
        String linea = DiagnosticoArranque.linea("startup.fail", "DatabaseConnection",
                new IllegalStateException("password=secreto123\nsegunda linea"));

        assertTrue(linea.contains("password=[oculto]"));
        assertFalse(linea.contains("secreto123"));
        assertFalse(linea.contains("\n"));
        assertTrue(linea.contains("\\n"));
    }

    @Test
    void escribeElDiagnosticoEnLaSalidaDeError() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PrintStream original = System.err;
        try (PrintStream reemplazo = new PrintStream(salida, true, StandardCharsets.UTF_8)) {
            System.setErr(reemplazo);
            DiagnosticoArranque.registrar("startup.fail", "ServerMain",
                    new IllegalStateException("fallo"));
        } finally {
            System.setErr(original);
        }

        assertTrue(salida.toString(StandardCharsets.UTF_8)
                .startsWith("event=startup.fail component=ServerMain error=IllegalStateException: fallo"));
    }
}
