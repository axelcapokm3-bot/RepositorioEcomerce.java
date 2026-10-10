package com.CapaAplicacion.DTOsalida;

public class ConfiguracionResponseDTO {
    private String clave;
    private String tipo;
    private String descripcion;
    private String valor;
    private String categoria;

    public ConfiguracionResponseDTO(String clave, String tipo, String descripcion, String valor, String categoria) {
        this.clave = clave;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.valor = valor;
        this.categoria = categoria;
    }

    public String getClave() {
        return clave;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getValor() {
        return valor;
    }

    public String getCategoria() {
        return categoria;
    }
}