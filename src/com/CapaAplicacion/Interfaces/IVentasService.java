package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.Venta;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IVentasService {
  
  CompletableFuture<Void> crearVentaAsync(final Carrito carrito, final  String tipoPago ,final IDescuentos descuento) ;
    CompletableFuture<Optional<Venta>> buscarPorIdAsync(final int id);
    

    CompletableFuture<Void> registrarPagoAsync(final int idVenta);
    

    CompletableFuture<Void> cancelarVentaAsync(final int idVenta);
}