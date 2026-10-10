package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.EstadoPago;

import java.util.List;
import java.util.Optional;

public interface IPagoRepository {
    /** Inserta un intento de pago y completa su id generado. */
    void insertar(Pago pago);

    List<Pago> buscarPorVenta(int idVenta);

    Optional<Pago> buscarPorMpPaymentId(String mpPaymentId);

    Optional<Pago> buscarPorOrderId(String orderId);

    Optional<Pago> buscarPorClaveIdempotenciaPasarela(String claveIdempotenciaPasarela);

    void actualizarCheckout(int id, String externalId, String orderId, String urlPago,
                            EstadoPago estado, String rawResponse);

    void actualizarEstado(int id, EstadoPago estado, String rawResponse);

    void actualizarWebhook(int id, String mpPaymentId, String orderId,
                           EstadoPago estado, String rawResponse);
}
