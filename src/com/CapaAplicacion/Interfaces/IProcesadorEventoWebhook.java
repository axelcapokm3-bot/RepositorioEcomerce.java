package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Excepciones.WebhookProcessingException;

public interface IProcesadorEventoWebhook {
    void procesar(EventoWebhook evento) throws WebhookProcessingException;
}
