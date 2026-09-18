package com.CapaAplicacion.Interfaces;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
public interface IServiceExecutor {
    //ejecutamos la tarea sin retorno 
    CompletableFuture<Void> ejecutar(Runnable tarea);
    //ejecutamos y devolvemos un valor 
   <T> CompletableFuture<T> ejecutar(Callable<T> tarea);
/** Cuántas tareas están esperando en la cola. */
    int getColaActual();
/** Cuántos hilos están trabajando ahora. */
    int getHiloActivos();
   /** Cuántos hilos existen en total en el pool. */
    int getHilosTotales();
   /** Tipo de servicio al que pertenece este executor. */
    TipoServicio getTipo();
   /** Cierra el pool de forma ordenada. */
    void shutdown();
}
