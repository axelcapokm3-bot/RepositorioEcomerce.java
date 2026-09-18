package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INotificadorEmail;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.VentasEventos;

/**
 * Adaptador técnico de infraestructura que escucha eventos de ventas publicados
 * en el EventBus y despacha las notificaciones de manera asíncrona mediante el ejecutor de NOTIFICACIONES.
 */
public class NotificacionListener {

    private final INotificadorEmail notificador;
    private final IServiceExecutor executor;

    public NotificacionListener(EventBus eventBus, INotificadorEmail notificador) {
        if (eventBus == null) {
            throw new IllegalArgumentException("El EventBus no puede ser nulo.");
        }
        if (notificador == null) {
            throw new IllegalArgumentException("El notificador no puede ser nulo.");
        }

        this.notificador = notificador;
        this.executor = RegistroExecutors.obtener(TipoServicio.NOTIFICACIONES);

        eventBus.suscribirse(VentasEventos.class, this::procesarEvento);
    }

    private void procesarEvento(VentasEventos evento) {
        executor.ejecutar(() -> {
            try {
                System.out.println("🔔 Evento: " + evento.getTipoOperacion()
                        + " | Venta ID: " + evento.getIdVenta()
                        + " | Hilo: " + Thread.currentThread().getName());

                switch (evento.getTipoOperacion()) {
                    case "CREADA":
                        notificador.enviarConfirmacionVenta(evento.getVentaSnapshot());
                        break;
                    case "PAGADA":
                        notificador.enviarNotificacionPago(evento.getVentaSnapshot());
                        break;
                    case "CANCELADA":
                        System.out.println("📧 Venta cancelada: no se envía email.");
                        break;
                    default:
                        System.out.println("📧 Evento no manejado: " + evento.getTipoOperacion());
                }
            } catch (Exception e) {
                // TODO: contador de fallos para alertas
                System.err.println("❌ Falló notificación para evento "
                        + evento.getTipoOperacion() + ": " + e.getMessage());
            }
        });
    }
}
