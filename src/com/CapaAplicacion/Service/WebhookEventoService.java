package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.IEventoWebhookRepository;
import com.CapaAplicacion.Interfaces.IProcesadorEventoWebhook;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.CapaDominio.Excepciones.WebhookProcessingException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Orquesta el registro durable y el procesamiento reintentable de webhooks. */
public class WebhookEventoService {
    private static final Logger LOGGER = Logger.getLogger(WebhookEventoService.class.getName());
    private static final int MAX_LONGITUD_ERROR = 1000;

    private final IEventoWebhookRepository repositorio;
    private final IProcesadorEventoWebhook procesador;
    private final IServiceExecutor executor;
    private final int maxIntentos;
    private final int lote;

    public WebhookEventoService(IEventoWebhookRepository repositorio,
                                IProcesadorEventoWebhook procesador,
                                IServiceExecutor executor,
                                int maxIntentos,
                                int lote) {
        if (repositorio == null || procesador == null || executor == null) {
            throw new IllegalArgumentException(
                    "El repositorio, el procesador y el executor de webhooks son obligatorios.");
        }
        if (maxIntentos <= 0) {
            throw new IllegalArgumentException("El máximo de intentos debe ser positivo.");
        }
        if (lote <= 0) {
            throw new IllegalArgumentException("El tamaño del lote debe ser positivo.");
        }
        this.repositorio = repositorio;
        this.procesador = procesador;
        this.executor = executor;
        this.maxIntentos = maxIntentos;
        this.lote = lote;
    }

    public boolean registrar(ProveedorWebhook proveedor, String tipo, String eventoId, String payload) {
        if (proveedor == null) {
            throw new IllegalArgumentException("El proveedor del webhook es obligatorio.");
        }
        if (tipo == null || tipo.isBlank() || tipo.length() > 100) {
            throw new IllegalArgumentException("El tipo del webhook no es válido.");
        }
        if (eventoId == null || eventoId.isBlank() || eventoId.length() > 255) {
            throw new IllegalArgumentException("El identificador del webhook no es válido.");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El cuerpo del webhook es obligatorio.");
        }
        return repositorio.registrar(new EventoWebhook(proveedor, tipo, eventoId, payload));
    }

    public void procesarPendientesAsync() {
        executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                procesarPendientes();
            }
        });
    }

    public int procesarPendientes() {
        List<EventoWebhook> pendientes = repositorio.listarPendientes(lote);
        int procesados = 0;
        for (EventoWebhook evento : pendientes) {
            if (procesarEvento(evento)) {
                procesados++;
            }
        }
        return procesados;
    }

    public boolean procesarEvento(EventoWebhook evento) {
        if (evento == null || evento.getId() == null) {
            return false;
        }
        if (!repositorio.marcarProcesando(evento.getId())) {
            return false;
        }
        evento.setIntentos(evento.getIntentos() + 1);
        try {
            procesador.procesar(evento);
            repositorio.marcarProcesado(evento.getId());
            return true;
        } catch (WebhookProcessingException exception) {
            registrarFallo(evento, exception);
            return false;
        } catch (RuntimeException exception) {
            registrarFallo(evento, new WebhookProcessingException(
                    "Error inesperado al procesar el webhook.", true, exception));
            return false;
        }
    }

    public int recuperarProcesandoAntiguos(int minutos) {
        if (minutos <= 0) {
            throw new IllegalArgumentException("Los minutos de recuperación deben ser positivos.");
        }
        return repositorio.recuperarProcesandoAntiguos(minutos);
    }

    private void registrarFallo(EventoWebhook evento, WebhookProcessingException exception) {
        String error = truncar(exception.getMessage());
        if (!exception.isReintentable() || evento.getIntentos() >= maxIntentos) {
            repositorio.marcarFallido(evento.getId(), error);
            LOGGER.log(Level.WARNING, "Webhook " + evento.getId()
                    + " quedó fallido tras " + evento.getIntentos() + " intento(s): " + error,
                    exception);
            return;
        }
        repositorio.marcarReintento(evento.getId(), error);
        LOGGER.log(Level.INFO, "Webhook " + evento.getId()
                + " se reintentará (intento " + evento.getIntentos() + " de "
                + maxIntentos + "): " + error);
    }

    private String truncar(String mensaje) {
        String valor = mensaje == null || mensaje.isBlank() ? "Error sin detalle." : mensaje;
        return valor.length() <= MAX_LONGITUD_ERROR
                ? valor
                : valor.substring(0, MAX_LONGITUD_ERROR);
    }
}
