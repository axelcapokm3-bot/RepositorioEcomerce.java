package com.CapaDominio.Entidades;

import java.time.LocalDateTime;

public class ConfiguracionValor {
    private long idNegocio;
    private long idConfiguracion;
    private String valor;
    private LocalDateTime fechaActualizacion;

    public ConfiguracionValor(long idNegocio, long idConfiguracion, String valor, LocalDateTime fechaActualizacion) {
        this.idNegocio = idNegocio;
        this.idConfiguracion = idConfiguracion;
        this.valor = valor;
        this.fechaActualizacion = fechaActualizacion;
    }

    public long getIdNegocio() { return idNegocio; }
    public long getIdConfiguracion() { return idConfiguracion; }
    public String getValor() { return valor; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
