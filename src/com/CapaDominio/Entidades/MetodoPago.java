package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class MetodoPago {
    private final int idCliente;
    private final int claveTarjeta;
    private BigDecimal saldoDisponible;

    public MetodoPago(int idCliente, int claveTarjeta, BigDecimal saldoDisponible) {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID del cliente debe ser válido.");
        }
        if (claveTarjeta <= 0 || claveTarjeta > 9999) {
            throw new IllegalArgumentException("La clave de la tarjeta debe ser un número válido de 4 dígitos.");
        }
        if (saldoDisponible == null || saldoDisponible.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo disponible no puede ser nulo ni negativo.");
        }

        this.idCliente = idCliente;
        this.claveTarjeta = claveTarjeta;
        this.saldoDisponible = saldoDisponible;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public int getClaveTarjeta() {
        return claveTarjeta;
    }

    public BigDecimal getSaldoDisponible() {
        return saldoDisponible;
    }
        
    public void debitar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a debitar debe ser mayor a cero.");
        }
        if (this.saldoDisponible.compareTo(monto) < 0) {
            throw new IllegalStateException("Saldo insuficiente para realizar la operación.");
        }
        this.saldoDisponible = this.saldoDisponible.subtract(monto);
    }

    public void acreditar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a acreditar debe ser mayor a cero.");
        }
        this.saldoDisponible = this.saldoDisponible.add(monto);
    }

    public void modificarSaldo(BigDecimal nuevoSaldo) {
        if (nuevoSaldo == null || nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo disponible debe ser un valor positivo.");
        }
        this.saldoDisponible = nuevoSaldo;
    }
}
