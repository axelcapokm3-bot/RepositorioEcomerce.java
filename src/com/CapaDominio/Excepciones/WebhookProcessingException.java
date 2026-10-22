package com.CapaDominio.Excepciones;

/** Fallo al procesar un evento de webhook; indica si conviene reintentarlo. */
public class WebhookProcessingException extends BusinessException {
    private final boolean reintentable;

    public WebhookProcessingException(String mensaje, boolean reintentable) {
        super(mensaje);
        this.reintentable = reintentable;
    }

    public WebhookProcessingException(String mensaje, boolean reintentable, Throwable causa) {
        super(mensaje, causa);
        this.reintentable = reintentable;
    }

    public boolean isReintentable() {
        return reintentable;
    }
}
