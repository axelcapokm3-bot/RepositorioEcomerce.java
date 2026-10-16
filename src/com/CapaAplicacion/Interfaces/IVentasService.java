package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.Venta;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IVentasService {
  
  CompletableFuture<Void> crearVentaAsync(final long idNegocio, final Carrito carrito, final  String tipoPago ,final IDescuentos descuento) ;
    CompletableFuture<Void> crearVentaAsync(final long idNegocio, final int idCliente, final String tipoPago, final IDescuentos descuento);
    CompletableFuture<ResultadoPago> crearVentaConPagoAsync(
            long idNegocio, Carrito carrito, String tipoPago, IDescuentos descuento,
            String claveIdempotenciaSolicitud);
    CompletableFuture<Optional<Venta>> buscarPorIdAsync(final long idNegocio, final int id);

    CompletableFuture<ResultadoPago> registrarPagoAsync(final long idNegocio, final int idVenta);

    CompletableFuture<Void> cancelarVentaAsync(final long idNegocio, final int idVenta);
}