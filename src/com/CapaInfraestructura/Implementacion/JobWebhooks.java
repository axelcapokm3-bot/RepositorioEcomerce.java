package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Service.WebhookEventoService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Procesa periódicamente los eventos Outbox pendientes. */
public class JobWebhooks implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(JobWebhooks.class.getName());
    private final WebhookEventoService servicio;
    private final ScheduledExecutorService scheduler;
    private final int intervalo;

    public JobWebhooks(WebhookEventoService servicio, int intervalo, int recuperacionMinutos) {
        if (servicio == null) throw new IllegalArgumentException("El servicio de webhooks es obligatorio.");
        if (intervalo <= 0) throw new IllegalArgumentException("El intervalo debe ser positivo.");
        if (recuperacionMinutos <= 0) {
            throw new IllegalArgumentException("Los minutos de recuperación deben ser positivos.");
        }
        this.servicio = servicio;
        this.intervalo = intervalo;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "webhook-outbox");
                hilo.setDaemon(true);
                return hilo;
            }
        });
        try {
            servicio.recuperarProcesandoAntiguos(recuperacionMinutos);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING,
                    "No se pudieron recuperar webhooks en proceso al iniciar.", exception);
        }
    }

    public void iniciar() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    servicio.procesarPendientes();
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.SEVERE,
                            "Falló el procesamiento periódico de webhooks.", exception);
                }
            }
        }, 0, intervalo, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
