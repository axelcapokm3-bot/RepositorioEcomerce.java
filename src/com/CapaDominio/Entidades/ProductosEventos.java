package com.CapaDominio.Entidades;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;

public class ProductosEventos {
    private final int productoId;
    private final ProductoRequestDTO datos;
    private final AccionAuditoria accion;

    public ProductosEventos(int productoId, ProductoRequestDTO datos) {
        this(productoId, datos, AccionAuditoria.CREAR);
    }

    public ProductosEventos(int productoId, ProductoRequestDTO datos, AccionAuditoria accion) {
        this.productoId = productoId;
        this.datos = datos;
        this.accion = accion;
    }

    public AccionAuditoria getAccion() {
        return accion;
    }

    public int getProductoId() {
        return productoId;
    }

    public ProductoRequestDTO getDatos() {
        return datos;
    }

    
}
