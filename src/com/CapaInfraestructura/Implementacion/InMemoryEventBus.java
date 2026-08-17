package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.EventBus;
import java.util.*;
import java.util.function.Consumer;

public class InMemoryEventBus implements EventBus {
    
    // Mapa que guarda: Clase del evento -> Lista de acciones (suscriptores)
    private final Map<Class<?>, List<Consumer<Object>>> suscriptores = new HashMap<>();

    @Override
    public <T> void publicar(T evento) {
        List<Consumer<Object>> acciones = suscriptores.get(evento.getClass());
        if (acciones != null) {
            for (Consumer<Object> accion : acciones) {
                // Ejecuta la lógica registrada para este evento
                accion.accept(evento);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void suscribirse(Class<T> tipoEvento, Consumer<T> suscriptor) {
        suscriptores.computeIfAbsent(tipoEvento, k -> new ArrayList<>())
                    .add(evento -> suscriptor.accept((T) evento));
    }
}