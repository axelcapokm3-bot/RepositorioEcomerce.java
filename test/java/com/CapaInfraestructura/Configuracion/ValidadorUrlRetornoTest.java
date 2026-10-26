package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import org.junit.jupiter.api.Test;

class ValidadorUrlRetornoTest {

    @Test
    void aceptaHttpsPublicaConPlaceholderYAllowlist() {
        String url = "https://shop.example.com/checkout?session_id={CHECKOUT_SESSION_ID}";

        String resultado = ValidadorUrlRetorno.validar(
                url, "éxito", "https://shop.example.com", true);

        assertEquals(url, resultado);
    }

    @Test
    void rechazaHttpPublicaEnProduccion() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "http://shop.example.com/checkout", "éxito",
                        "https://shop.example.com", true));
    }

    @Test
    void rechazaOrigenFueraDeLaAllowlist() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://evil.example.com/checkout", "éxito",
                        "https://shop.example.com", true));
    }

    @Test
    void rechazaCredencialesFragmentoYLocalhostEnProduccion() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://usuario:clave@shop.example.com/checkout", "éxito",
                        "https://shop.example.com", true));
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://shop.example.com/checkout#fragmento", "éxito",
                        "https://shop.example.com", true));
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://localhost:5500/checkout", "éxito",
                        "https://localhost:5500", true));
    }

    @Test
    void rechazaIpPrivadaEnProduccion() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://192.168.1.10/checkout", "éxito",
                        "https://192.168.1.10", true));
    }

    @Test
    void exigeAllowlistEnProduccion() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> ValidadorUrlRetorno.validar(
                        "https://shop.example.com/checkout", "éxito", null, true));
    }

    @Test
    void permiteLocalhostHttpEnDesarrolloConAllowlist() {
        String resultado = ValidadorUrlRetorno.validar(
                "http://localhost:5500/index.html?session_id={CHECKOUT_SESSION_ID}",
                "éxito",
                "http://localhost:5500",
                false);

        assertEquals("http://localhost:5500/index.html?session_id={CHECKOUT_SESSION_ID}", resultado);
    }
}
