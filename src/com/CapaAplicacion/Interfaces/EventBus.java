package com.CapaAplicacion.Interfaces;
import java.util.function.Consumer;

public interface EventBus {
    <T> void publicar(T evento);
    <T> void suscribirse(Class<T> tipoEvento, Consumer<T> suscriptor);
}