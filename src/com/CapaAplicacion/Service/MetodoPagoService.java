package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.IMetodoPagoRepositorio;
import com.CapaDominio.Entidades.MetodoPago;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MetodoPagoService {

    private final IMetodoPagoRepositorio metodoPagoRepositorio;

    public MetodoPagoService(IMetodoPagoRepositorio metodoPagoRepositorio) {
        this.metodoPagoRepositorio = metodoPagoRepositorio;
    }

    public CompletableFuture<Void> asociarMetodoPagoAsync(int idCliente, int claveTarjeta) {
        return CompletableFuture.runAsync(() -> {
            if (idCliente <= 0) {
                throw new IllegalArgumentException("ID de cliente inválido.");
            }
            if (claveTarjeta <= 0 || claveTarjeta > 9999) {
                throw new IllegalArgumentException("La clave debe ser un número válido de 4 dígitos.");
            }

            Optional<MetodoPago> existente = metodoPagoRepositorio.buscarPorIdCliente(idCliente);
            if (existente.isPresent()) {
                throw new IllegalStateException("El cliente ya tiene un método de pago asociado.");
            }

            // Saldo semilla simulado por la entidad bancaria
            BigDecimal saldoSemillaBancaria = new BigDecimal("50000.00");
            MetodoPago nuevoMetodo = new MetodoPago(idCliente, claveTarjeta, saldoSemillaBancaria);
            metodoPagoRepositorio.guardar(nuevoMetodo);
        });
    }

    public CompletableFuture<Optional<MetodoPago>> obtenerPorClienteAsync(int idCliente) {
        return CompletableFuture.supplyAsync(() -> metodoPagoRepositorio.buscarPorIdCliente(idCliente));
    }
}
