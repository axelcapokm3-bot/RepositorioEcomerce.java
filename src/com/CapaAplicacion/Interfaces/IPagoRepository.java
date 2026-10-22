package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.EstadoPago;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface IPagoRepository {
    /** Inserta un intento de pago y completa su id generado. */
    void insertar(Pago pago);

    Optional<Pago> buscarPorId(int id);

    default Optional<Pago> buscarPorIdParaUpdate(int id, Connection connection)
            throws java.sql.SQLException {
        return buscarPorId(id);
    }

    default Optional<Pago> buscarPendientePorVentaParaUpdate(long idNegocio, int idVenta,
                                                             Connection connection)
            throws java.sql.SQLException {
        for (Pago pago : buscarPorVenta(idNegocio, idVenta)) {
            if (pago.getEstado() == EstadoPago.PENDIENTE) {
                return Optional.of(pago);
            }
        }
        return Optional.empty();
    }

    List<Pago> buscarPorVenta(long idNegocio, int idVenta);

    List<Pago> buscarCandidatosMercadoPagoPorVenta(int idVenta);

    Optional<Pago> buscarPorMpPaymentId(String mpPaymentId);

    Optional<Pago> buscarPorOrderId(String orderId);

    Optional<Pago> buscarPorClaveIdempotenciaPasarela(long idNegocio, String claveIdempotenciaPasarela);

    void actualizarCheckout(int id, String externalId, String orderId, String urlPago,
                            EstadoPago estado, String rawResponse);

    void actualizarEstado(int id, EstadoPago estado, String rawResponse);

    void actualizarEstado(int id, EstadoPago estado, String rawResponse,
                          Connection connection) throws java.sql.SQLException;

    void actualizarWebhook(int id, String mpPaymentId, String orderId,
                           EstadoPago estado, String rawResponse);

    void actualizarWebhook(int id, String mpPaymentId, String orderId,
                           EstadoPago estado, String rawResponse,
                           Connection connection) throws java.sql.SQLException;
}
