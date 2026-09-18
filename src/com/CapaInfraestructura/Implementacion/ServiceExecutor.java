package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Executor que se configura leyendo la constante TipoServicio
public class ServiceExecutor implements IServiceExecutor {

    private final TipoServicio tipo;
    private final ThreadPoolExecutor executor;

    public ServiceExecutor(TipoServicio tipo) {
        this.tipo = tipo;

        this.executor = new ThreadPoolExecutor(
            tipo.getTamanoCore(),
            tipo.getTamanoMax(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(tipo.getCapacidadCola()),
            crearFabricaDeHilos(tipo.getPrefijoHilo()),
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure natural si se satura la cola
        );

        // Si los hilos core están inactivos, se destruyen para liberar RAM
        this.executor.allowCoreThreadTimeOut(true);
    }

    /*
     * Crea hilos con nombre personalizado y manejador de excepciones global por hilo.
     * Así en los logs verás "venta-worker-3" en lugar de "pool-1-thread-3".
     */
    private static ThreadFactory crearFabricaDeHilos(String prefijo) {
        AtomicInteger contador = new AtomicInteger(1);
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, prefijo + "-" + contador.getAndIncrement());
                hilo.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread h, Throwable e) {
                        System.err.println("❌ Error no capturado en " + h.getName() + ": " + e.getMessage());
                    }
                });
                return hilo;
            }
        };
    }

    @Override
    public CompletableFuture<Void> ejecutar(Runnable tarea) {
        return CompletableFuture.runAsync(tarea, executor);
    }

    @Override
    public <T> CompletableFuture<T> ejecutar(Callable<T> tarea) {
        return CompletableFuture.supplyAsync(new Supplier<T>() {
            @Override
            public T get() {
                try {
                    return tarea.call();
                } catch (Exception e) {
                    // Envolvemos la excepción en RuntimeException para que CompletableFuture la propague
                    throw new CompletionException(e);
                }
            }
        }, executor);
    }

    @Override
    public int getColaActual() { 
        return executor.getQueue().size(); 
    }

    @Override
    public int getHiloActivos() { 
        return executor.getActiveCount(); 
    }

    @Override
    public int getHilosTotales() { 
        return executor.getPoolSize(); 
    }

    @Override
    public TipoServicio getTipo() { 
        return tipo; 
    }

    @Override
    public void shutdown() {
        System.out.println("🛑 Cerrando executor: " + tipo.name());
        executor.shutdown();
        try {
            // Espera hasta 30 segundos a que terminen las tareas en curso
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Fuerza el cierre si vence el tiempo
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restaura el estado de interrupción
        }
    }
}