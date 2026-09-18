package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INotificadorEmail;
import com.CapaDominio.Entidades.Venta;

/**
 * Implementación simulada (Mock) de INotificadorEmail para entorno de desarrollo y pruebas.
 * Emite los detalles de la notificación por consola sin conectarse a servidores SMTP externos.
 */
public class NotificadorEmailMock implements INotificadorEmail {

    @Override
    public void enviarConfirmacionVenta(Venta venta) {
        if (venta == null) {
            System.out.println("📧 [MOCK] Confirmación de venta omitida: venta nula | Hilo: "
                    + Thread.currentThread().getName());
            return;
        }
        System.out.println("📧 [MOCK] Confirmación de venta enviada | Venta ID: " + venta.getId()
                + " | Cliente ID: " + venta.getIdCliente()
                + " | Total: $" + venta.calculoTotal()
                + " | Hilo: " + Thread.currentThread().getName());
    }

    @Override
    public void enviarNotificacionPago(Venta venta) {
        if (venta == null) {
            System.out.println("💳 [MOCK] Notificación de pago omitida: venta nula | Hilo: "
                    + Thread.currentThread().getName());
            return;
        }
        System.out.println("💳 [MOCK] Notificación de pago enviada | Venta ID: " + venta.getId()
                + " | Cliente ID: " + venta.getIdCliente()
                + " | Total: $" + venta.calculoTotal()
                + " | Estado: " + venta.getEstadoCompra()
                + " | Hilo: " + Thread.currentThread().getName());
    }

    @Override
    public void enviarAlertaStockBajo(int idProducto, String nombreProducto) {
        System.out.println("⚠️ [MOCK] Alerta de stock bajo enviada | Producto ID: " + idProducto
                + " | Nombre: " + nombreProducto
                + " | Hilo: " + Thread.currentThread().getName());
    }
}
