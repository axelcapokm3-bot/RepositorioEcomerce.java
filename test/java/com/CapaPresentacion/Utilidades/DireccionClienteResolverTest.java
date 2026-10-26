package com.CapaPresentacion.Utilidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DireccionClienteResolverTest {

    private static final String CLAVE = DireccionClienteResolver.CLAVE_PROXIES_CONFIABLES;

    @AfterEach
    void tearDown() {
        System.clearProperty(CLAVE);
    }

    @Test
    @DisplayName("usa la IP remota cuando no hay proxies confiables")
    void sinProxiesConfiaEnRemota() {
        HttpExchange exchange = exchange("203.0.113.10",
                "X-Forwarded-For", "198.51.100.7");
        assertEquals("203.0.113.10", DireccionClienteResolver.resolver(exchange));
    }

    @Test
    @DisplayName("toma la IP del cliente desde X-Forwarded-For tras un proxy confiable")
    void tomaClienteDesdeReenvio() {
        System.setProperty(CLAVE, "10.0.0.5");
        HttpExchange exchange = exchange("10.0.0.5",
                "X-Forwarded-For", "198.51.100.7, 10.0.0.5");
        assertEquals("198.51.100.7", DireccionClienteResolver.resolver(exchange));
    }

    @Test
    @DisplayName("ignora entradas inválidas y usa X-Real-IP como respaldo")
    void usaRealIpComoRespaldo() {
        System.setProperty(CLAVE, "10.0.0.5");
        HttpExchange exchange = exchange("10.0.0.5", "X-Real-IP", "198.51.100.9");
        assertEquals("198.51.100.9", DireccionClienteResolver.resolver(exchange));
    }

    @Test
    @DisplayName("no confía en un encabezado enviado por un cliente directo")
    void noConfiaEnClienteDirecto() {
        System.setProperty(CLAVE, "10.0.0.5");
        HttpExchange exchange = exchange("203.0.113.10",
                "X-Forwarded-For", "198.51.100.7");
        assertEquals("203.0.113.10", DireccionClienteResolver.resolver(exchange));
    }

    @Test
    @DisplayName("descarta valores que no son direcciones IP")
    void descartaValoresInvalidos() {
        System.setProperty(CLAVE, "10.0.0.5");
        HttpExchange exchange = exchange("10.0.0.5",
                "X-Forwarded-For", "evil.example, 10.0.0.5");
        assertEquals("10.0.0.5", DireccionClienteResolver.resolver(exchange));
    }

    private HttpExchange exchange(String remota, String encabezado, String valor) {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.set(encabezado, valor);
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRemoteAddress()).thenReturn(
                new InetSocketAddress(remota, 12345));
        return exchange;
    }
}
