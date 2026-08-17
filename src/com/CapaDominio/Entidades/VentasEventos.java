package com.CapaDominio.Entidades;

import java.time.LocalDateTime;

public class VentasEventos {
    private final int idVenta;
    private final String tipoOperacion; 
    private final Venta ventaSnapshot;   
    private final LocalDateTime fechaOcurrencia;


    public VentasEventos(String tipoOperacion, Venta venta) {
        this.idVenta = venta.getId();
        this.tipoOperacion = tipoOperacion;
        this.ventaSnapshot = venta;
        this.fechaOcurrencia = LocalDateTime.now();
    }


    public int getIdVenta() { return idVenta; }
    public String getTipoOperacion() { return tipoOperacion; }
    public Venta getVentaSnapshot() { return ventaSnapshot; }
    public LocalDateTime getFechaOcurrencia() { return fechaOcurrencia; }
}