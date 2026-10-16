package com.CapaDominio.Entidades;

public class ConfiguracionCatalogo {
    private long id;
    private String clave;
    private String tipo;
    private String descripcion;
    private String valorDefault;
    private String categoria;

    public ConfiguracionCatalogo(long id, String clave, String tipo, String descripcion, String valorDefault, String categoria) {
        if (clave == null || clave.trim().isEmpty()) {
            throw new IllegalArgumentException("La clave de configuración no puede estar vacía.");
        }
        this.id = id;
        this.clave = clave;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.valorDefault = valorDefault;
        this.categoria = categoria;
    }

    public long getId() { return id; }
    public String getClave() { return clave; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getValorDefault() { return valorDefault; }
    public String getCategoria() { return categoria; }
}
