package com.CapaDominio.Entidades;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Auditoria {
    private long id;
    private AccionAuditoria accion;
    private String entidad;
    private int idEntidad;
    private String usuario;
    private String ipOrigen;
    private String userAgent;
    private String resultado;
    private String descripcion;
    private String detalle;
    private LocalDateTime fecha;

    public Auditoria() {
        this.resultado = "OK";
        this.fecha = LocalDateTime.now();
        this.entidad = "Venta";
    }

    public Auditoria(AccionAuditoria accion, int idEntidad, String descripcion) {
        if (descripcion == null || descripcion.isBlank()) { 
            throw new IllegalArgumentException("La descripción no puede estar vacía ni ser nula.");
        } 
        if (accion == null) { 
           throw new IllegalArgumentException("La acción no puede ser nula."); 
        }
        if (idEntidad < 0) {
            throw new IllegalArgumentException("El ID de la entidad no puede ser negativo.");
        }

        this.accion = accion;
        this.idEntidad = idEntidad;
        this.descripcion = descripcion;
        this.entidad = "Venta";
        this.resultado = "OK";
        this.fecha = LocalDateTime.now();
    }

    public Auditoria(long id, AccionAuditoria accion, String entidad, int idEntidad, 
                     String usuario, String ipOrigen, String userAgent, 
                     String resultado, String descripcion, String detalle, LocalDateTime fecha) {
        this.id = id;
        this.accion = accion;
        this.entidad = entidad != null ? entidad : "Venta";
        this.idEntidad = idEntidad;
        this.usuario = usuario;
        this.ipOrigen = ipOrigen;
        this.userAgent = userAgent;
        this.resultado = resultado != null ? resultado : "OK";
        this.descripcion = descripcion != null ? descripcion : "";
        this.detalle = detalle;
        this.fecha = fecha != null ? fecha : LocalDateTime.now();
    }

    public static Auditoria registrar(AccionAuditoria accion, int idEntidad, String descripcion) {
        return new Auditoria(accion, idEntidad, descripcion);
    }

    public boolean esDelDiaDeHoy() {
        return this.fecha != null && this.fecha.toLocalDate().equals(LocalDate.now());
    }

    public String formatearParaLog() {
        return String.format("📜 [%s] | Acción: %s | Entidad: %s | Entidad ID: %d | Detalle: %s",
                fecha != null ? fecha.toString() : "N/A", accion, entidad, idEntidad, descripcion);
    }

    // Getters y Setters
    public long getId() { return id; }
    public void setId(long id) {
        if (this.id != 0 && this.id != id) {
            throw new IllegalStateException("El ID ya ha sido asignado y no puede modificarse.");
        }
        this.id = id;
    }

    // Compatibilidad por si algún código antiguo usa int para id
    public void setId(int id) {
        setId((long) id);
    }

    public AccionAuditoria getAccion() { return accion; }
    public void setAccion(AccionAuditoria accion) { this.accion = accion; }

    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }

    public int getIdEntidad() { return idEntidad; }
    public void setIdEntidad(int idEntidad) { this.idEntidad = idEntidad; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
