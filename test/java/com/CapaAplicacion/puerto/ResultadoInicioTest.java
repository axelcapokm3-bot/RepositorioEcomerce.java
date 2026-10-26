package com.CapaAplicacion.puerto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResultadoInicioTest {

    @Test
    void exponeLosCamposDelResultado() {
        ResultadoInicio resultado = new ResultadoInicio(
                "cs_test_123",
                "https://checkout.stripe.com/c/pay/cs_test_123",
                EstadoPagoExterno.PENDIENTE,
                "{\"id\":\"cs_test_123\"}");

        assertEquals("cs_test_123", resultado.idExterno());
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", resultado.urlPago());
        assertSame(EstadoPagoExterno.PENDIENTE, resultado.estado());
        assertEquals("{\"id\":\"cs_test_123\"}", resultado.rawResponse());
    }

    @Test
    void exigeIdentificadorUrlYEstado() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoInicio(" ", "https://pago.example", EstadoPagoExterno.PENDIENTE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoInicio("id", " ", EstadoPagoExterno.PENDIENTE, null));
        assertThrows(NullPointerException.class,
                () -> new ResultadoInicio("id", "https://pago.example", null, null));
    }
}
