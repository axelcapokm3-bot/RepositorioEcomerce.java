package com.CapaDominio.Entidades;

public enum AccionAuditoria {
CREAR("PRODUCTO_CREADO"),
ACTUALIZAR("PRODUCTO_ACTUALIZADO"),
ELIMINAR("PRODUCTO_ELIMINADO"),
REGISTRAR_VENTA("VENTA_REGISTRADA"),
CANCELAR_VENTA("VENTA_CANCELADA");

    private final String descripcion;

    // Constructor privado para los enums
    AccionAuditoria(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}