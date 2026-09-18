package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Venta;

/**
 * Contrato para el servicio de envío de notificaciones por correo electrónico.
 */
public interface INotificadorEmail {

    /**
     * Envía un correo de confirmación tras la creación de una venta.
     *
     * @param venta la entidad de venta asociada.
     */
    void enviarConfirmacionVenta(Venta venta);

    /**
     * Envía una notificación informando que el pago ha sido procesado.
     *
     * @param venta la entidad de venta pagada.
     */
    void enviarNotificacionPago(Venta venta);

    /**
     * Envía una alerta administrativa informando que un producto tiene bajo stock.
     *
     * @param idProducto identificador único del producto.
     * @param nombreProducto nombre descriptivo del producto.
     */
    void enviarAlertaStockBajo(int idProducto, String nombreProducto);
}
