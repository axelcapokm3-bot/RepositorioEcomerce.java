package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.AdquiridorLock;
import com.CapaAplicacion.Service.VentaService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Expira periódicamente ventas PENDIENTE y libera su stock con lock global en Redis. */
public class JobExpiracionVentas implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(JobExpiracionVentas.class.getName());
    private static final String CLAVE_LOCK = "job:expiracion_ventas";
    private static final long TTL_LOCK_SEGUNDOS = 300L;

    private final VentaService ventaService;
    private final AdquiridorLock adquiridorLock;
    private final ScheduledExecutorService scheduler;
    private final int intervalo;
    private final int minutos;
    private final int lote;

    public JobExpiracionVentas(VentaService ventaService, int intervalo, int minutos, int lote) {
        this(ventaService, null, intervalo, minutos, lote);
    }

    public JobExpiracionVentas(VentaService ventaService, AdquiridorLock adquiridorLock, int intervalo, int minutos, int lote) {
        if (ventaService == null) throw new IllegalArgumentException("VentaService es obligatorio.");
        if (intervalo <= 0) throw new IllegalArgumentException("El intervalo debe ser positivo.");
        if (minutos <= 0) throw new IllegalArgumentException("Los minutos deben ser positivos.");
        if (lote <= 0) throw new IllegalArgumentException("El lote debe ser positivo.");
        this.ventaService = ventaService;
        this.adquiridorLock = adquiridorLock;
        this.intervalo = intervalo;
        this.minutos = minutos;
        this.lote = lote;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "ventas-expiracion");
                hilo.setDaemon(true);
                return hilo;
            }
        });
    }

    public void iniciar() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                boolean adquirido = false;
                if (adquiridorLock != null) {
                    adquirido = adquiridorLock.intentarAdquirir(CLAVE_LOCK, TTL_LOCK_SEGUNDOS);
                    if (!adquirido) {
                        LOGGER.info("JobExpiracionVentas omitido: otra instancia ya posee el lock global en Redis.");
                        return;
                    }
                }
                try {
                    int expiradas = ventaService.expirarVentasPendientes(minutos, lote);
                    if (expiradas > 0) {
                        LOGGER.info("Se expiraron " + expiradas + " venta(s) pendiente(s).");
                    }
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.SEVERE,
                            "Falló la expiración periódica de ventas.", exception);
                } finally {
                    if (adquirido && adquiridorLock != null) {
                        try {
                            adquiridorLock.liberar(CLAVE_LOCK);
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.WARNING, "Error al liberar lock de expiración de ventas", e);
                        }
                    }
                }
            }
        }, intervalo, intervalo, TimeUnit.SECONDS);
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
