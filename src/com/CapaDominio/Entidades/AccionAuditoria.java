package com.CapaDominio.Entidades;

public enum AccionAuditoria {
    VENTA_CREADA,
    VENTA_PAGADA,
    VENTA_CANCELADA,
    PAGO_FALLIDO,
    STOCK_INSUFICIENTE,
    PRODUCTO_CREADO,
    PRODUCTO_ACTUALIZADO,
    CLIENTE_ALTA,
    CLIENTE_BAJA,
    CREAR,
    ACTUALIZAR,
    ELIMINAR,
    REGISTRAR_VENTA,
    CANCELAR_VENTA;

    public String getDescripcion() {
        return this.name();
    }
}
