package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanitizadorLogTest {

    @Test
    @DisplayName("oculta correos, tokens y secretos")
    void ocultaDatosSensibles() {
        String saneado = SanitizadorLog.texto(
                "usuario juan@mail.com Authorization: Bearer abc.def.ghi access_token=APP_USR-123");
        assertFalse(saneado.contains("juan@mail.com"));
        assertFalse(saneado.contains("abc.def.ghi"));
        assertFalse(saneado.contains("APP_USR-123"));
        assertTrue(saneado.contains("[correo]"));
        assertTrue(saneado.contains("[oculto]"));
    }

    @Test
    @DisplayName("enmascara números de tarjeta válidos por Luhn")
    void enmascaraTarjetas() {
        String saneado = SanitizadorLog.texto("pago con 4111 1111 1111 1111 aprobado");
        assertEquals("pago con [tarjeta] aprobado", saneado);
    }

    @Test
    @DisplayName("no enmascara identificadores numéricos comunes")
    void conservaIdentificadores() {
        assertTrue(SanitizadorLog.texto("venta 1234567890123").contains("1234567890123"));
    }

    @Test
    @DisplayName("resume una excepción sin exponer su mensaje sensible")
    void resumeErrores() {
        String resumen = SanitizadorLog.error(
                new IllegalStateException("fallo al enviar a cliente@mail.com"));
        assertTrue(resumen.startsWith("IllegalStateException:"));
        assertFalse(resumen.contains("cliente@mail.com"));
    }

    @Test
    @DisplayName("soporta mensajes nulos y extensos")
    void soportaCasosBorde() {
        assertEquals("error desconocido", SanitizadorLog.error(null));
        String largo = SanitizadorLog.texto("x".repeat(600));
        assertTrue(largo.endsWith("..."));
        assertTrue(largo.length() <= 503);
    }

    @Test
    @DisplayName("neutraliza saltos de línea y controles en logs")
    void neutralizaSaltosDeLinea() {
        String saneado = SanitizadorLog.linea("fila1\r\nfila2\t\u0000fila3\u2028fila4");
        assertEquals("fila1\\r\\nfila2\\t?fila3\\u2028fila4", saneado);

        String error = SanitizadorLog.error(new IllegalStateException("linea1\nlinea2"));
        assertEquals("IllegalStateException: linea1\\nlinea2", error);
    }
}
