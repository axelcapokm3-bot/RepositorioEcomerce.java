package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.EventoWebhook;

import java.util.List;
import java.util.Optional;

public interface IEventoWebhookRepository {
    /** Registra el evento; devuelve false si ya existía por su clave única. */
    boolean registrar(EventoWebhook evento);

    Optional<EventoWebhook> buscarPorId(int id);

    List<EventoWebhook> listarPendientes(int limite);

    boolean marcarProcesando(int id);

    void marcarProcesado(int id);

    void marcarReintento(int id, String error);

    void marcarFallido(int id, String error);

    int recuperarProcesandoAntiguos(int minutos);
}
