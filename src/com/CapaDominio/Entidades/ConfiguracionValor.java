package com.CapaDominio.Entidades;

import java.time.LocalDateTime;

public class ConfiguracionValor {
    private int idNegocio;
    private int idConfiguracion;
    private String valor;
    private LocalDateTime fechaActualizacion;

    public ConfiguracionValor(int idNegocio, int idConfiguracion, String valor, LocalDateTime fechaActualizacion) {
        this.idNegocio = idNegocio;
        this.idConfiguracion = idConfiguracion;
        this.valor = valor;
        this.fechaActualizacion = fechaActualizacion;
    }

    public int getIdNegocio() { return idNegocio; }
    public int getIdConfiguracion() { return idConfiguracion; }
    public String getValor() { return valor; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
