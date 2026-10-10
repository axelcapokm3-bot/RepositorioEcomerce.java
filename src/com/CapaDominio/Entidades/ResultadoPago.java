package com.CapaDominio.Entidades;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResultadoPago(
        boolean exitoso,
        String transaccionId,
        String mensaje,
        BigDecimal monto,
        LocalDateTime timestamp,
        String pasarelaUsada,
        String urlPago,
        EstadoPago estado,
        String paymentId,
        String rawResponse
) {
    public static ResultadoPago aprobado(String transaccionId, BigDecimal monto, String pasarela,
                                         String identificadorExterno, String rawResponse) {
        return new ResultadoPago(
                true,
                transaccionId,
                "Pago aprobado.",
                monto,
                LocalDateTime.now(),
                pasarela,
                null,
                EstadoPago.APROBADO,
                identificadorExterno,
                rawResponse);
    }

    public static ResultadoPago pendiente(String transaccionId, BigDecimal monto, String pasarela,
                                          String urlPago, String identificadorExterno, String rawResponse) {
        return pendiente(
                transaccionId,
                monto,
                pasarela,
                urlPago,
                identificadorExterno,
                rawResponse,
                "Pago pendiente de confirmación.");
    }

    public static ResultadoPago pendiente(String transaccionId, BigDecimal monto, String pasarela,
                                          String urlPago, String identificadorExterno, String rawResponse,
                                          String mensaje) {
        return new ResultadoPago(
                false,
                transaccionId,
                mensaje,
                monto,
                LocalDateTime.now(),
                pasarela,
                urlPago,
                EstadoPago.PENDIENTE,
                identificadorExterno,
                rawResponse);
    }

    public static ResultadoPago fallido(String transaccionId, BigDecimal monto, String pasarela,
                                        EstadoPago estado, String mensaje, String identificadorExterno,
                                        String rawResponse) {
        return new ResultadoPago(
                false,
                transaccionId,
                mensaje,
                monto,
                LocalDateTime.now(),
                pasarela,
                null,
                estado,
                identificadorExterno,
                rawResponse);
    }
}
