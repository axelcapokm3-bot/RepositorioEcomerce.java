package com.CapaDominio.Entidades;

import java.time.LocalDateTime;

public class Negocio {
    private long id;
    private String nombre;
    private String subdominio;
    private String emailContacto;
    private boolean activo;
    private LocalDateTime fechaAlta;
    private LocalDateTime fechaBaja;

    public Negocio(long id, String nombre, String subdominio, String emailContacto, boolean activo, LocalDateTime fechaAlta, LocalDateTime fechaBaja) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del negocio no puede estar vacío.");
        }
        if (subdominio == null || subdominio.trim().isEmpty()) {
            throw new IllegalArgumentException("El subdominio del negocio no puede estar vacío.");
        }
        this.id = id;
        this.nombre = nombre;
        this.subdominio = subdominio;
        this.emailContacto = emailContacto;
        this.activo = activo;
        this.fechaAlta = fechaAlta;
        this.fechaBaja = fechaBaja;
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getSubdominio() { return subdominio; }
    public String getEmailContacto() { return emailContacto; }
    public boolean isActivo() { return activo; }
    public LocalDateTime getFechaAlta() { return fechaAlta; }
    public LocalDateTime getFechaBaja() { return fechaBaja; }
}
