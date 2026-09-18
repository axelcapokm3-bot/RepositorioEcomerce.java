package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.MetodoPago;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IMetodoPagoService {
    CompletableFuture<Void> registrarMetodoPagoAsync(int idCliente, int claveTarjeta, java.math.BigDecimal saldoInicial);
    CompletableFuture<Optional<MetodoPago>> obtenerPorClienteAsync(int idCliente, int claveTarjeta);
    CompletableFuture<Void> actualizarSaldoAsync(int idCliente, int claveTarjeta, java.math.BigDecimal nuevoSaldo);
    CompletableFuture<List<MetodoPago>> listarTodosAsync();
    CompletableFuture<Void> eliminarAsync(int idCliente);
}