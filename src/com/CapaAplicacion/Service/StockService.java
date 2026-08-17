
package com.CapaAplicacion.Service;

import com.CapaDominio.Entidades.*;


public class StockService {

    private static final int UMBRAL_CRITICO = 5;

    public EstadoStock evaluarStock(Producto producto) {
        int cantidad = producto.getStockFisico();

        if (cantidad > UMBRAL_CRITICO) {
        return EstadoStock.DISPONIBLE;
    }

   if (cantidad == 0) {
        dispararAlertaReposicion(producto);
        return EstadoStock.AGOTADO;
    } 
    else if (cantidad > 0 && cantidad <= UMBRAL_CRITICO) {
        return EstadoStock.CRITICO;
    } 
    else {
        return EstadoStock.DISPONIBLE;
    }
}

    private void dispararAlertaReposicion(Producto producto) {
    
        System.out.println("ALERTA: El producto " + producto.getNombre() + " necesita reposición urgente.");
    }
}

