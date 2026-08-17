package com.CapaDominio.Entidades;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Auditoria {
    private int id;
    private final AccionAuditoria accion; 
    private final int idEntidad;
    private final LocalDateTime fecha;
    private final String descripcion;

    public Auditoria(AccionAuditoria accion, int idEntidad, String descripcion) {
        // 1. Bugfix: Validamos 'null' PRIMERO
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
        this.fecha = LocalDateTime.now(); // Marca de tiempo del momento exacto de creación
    }

    // =========================================================================
    // FACTORY METHODS (Creación semántica y limpia)
    // =========================================================================
    public static Auditoria registrar(AccionAuditoria accion, int idEntidad, String descripcion) {
        return new Auditoria(accion, idEntidad, descripcion);
    }

    // =========================================================================
    // LÓGICA DE CONSULTA / DOMINIO (Comportamiento de lectura)
    // =========================================================================
    
    /**
     * Evalúa si el registro se generó en la jornada actual.
     */
    public boolean esDelDiaDeHoy() {
        return this.fecha.toLocalDate().equals(LocalDate.now());
    }

    /**
     * Formatea el registro con un estándar listo para consola o archivo de Log.
     */
    public String formatearParaLog() {
        return String.format("📜 [%s] | Acción: %s | Entidad ID: %d | Detalle: %s",
                fecha.toString(), accion, idEntidad, descripcion);
    }

    // =========================================================================
    // GETTERS (Únicamente lectura, NADA de setters)
    // =========================================================================
    public AccionAuditoria getAccion() { return accion; }

    public int getId() { return id; }

    public void setId(int id) {
        if (this.id != 0) {
            throw new IllegalStateException("El ID ya ha sido asignado y no puede modificarse.");
        }
        this.id = id;
    }

    public int getIdEntidad() { return idEntidad; }
    public LocalDateTime getFecha() { return fecha; }
    public String getDescripcion() { return descripcion; }
}
