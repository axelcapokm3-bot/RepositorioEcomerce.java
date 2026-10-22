package com.CapaDominio.Entidades;

import java.time.LocalDateTime;

/** Evento crudo recibido de una pasarela, pendiente de procesamiento durable. */
public class EventoWebhook {
    private Integer id;
    private ProveedorWebhook proveedor;
    private String tipo;
    private String eventoId;
    private String payload;
    private EstadoEventoWebhook estado;
    private int intentos;
    private String ultimoError;
    private LocalDateTime fechaRecepcion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaProcesamiento;

    public EventoWebhook(ProveedorWebhook proveedor, String tipo, String eventoId, String payload) {
        this(null, proveedor, tipo, eventoId, payload, EstadoEventoWebhook.PENDIENTE,
                0, null, null, null, null);
    }

    public EventoWebhook(Integer id, ProveedorWebhook proveedor, String tipo, String eventoId,
                         String payload, EstadoEventoWebhook estado, int intentos,
                         String ultimoError, LocalDateTime fechaRecepcion,
                         LocalDateTime fechaActualizacion, LocalDateTime fechaProcesamiento) {
        this.id = id;
        this.proveedor = proveedor;
        this.tipo = tipo;
        this.eventoId = eventoId;
        this.payload = payload;
        this.estado = estado;
        this.intentos = intentos;
        this.ultimoError = ultimoError;
        this.fechaRecepcion = fechaRecepcion;
        this.fechaActualizacion = fechaActualizacion;
        this.fechaProcesamiento = fechaProcesamiento;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProveedorWebhook getProveedor() {
        return proveedor;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getPayload() {
        return payload;
    }

    public EstadoEventoWebhook getEstado() {
        return estado;
    }

    public void setEstado(EstadoEventoWebhook estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public LocalDateTime getFechaProcesamiento() {
        return fechaProcesamiento;
    }
}
