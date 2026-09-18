package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IMetodoPagoRepositorio;
import com.CapaDominio.Entidades.MetodoPago;
import java.sql.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioMetodoPago implements IMetodoPagoRepositorio {

    private final ConcurrentHashMap<Integer, MetodoPago> almacenamiento = new ConcurrentHashMap<>();

    @Override
    public void guardar(MetodoPago metodoPago) {
        if (metodoPago == null) {
            throw new IllegalArgumentException("El método de pago no puede ser nulo.");
        }
        almacenamiento.put(metodoPago.getIdCliente(), metodoPago);
    }

    @Override
    public Optional<MetodoPago> buscarPorIdCliente(int idCliente) {
        return Optional.ofNullable(almacenamiento.get(idCliente));
    }

    public Optional<MetodoPago> buscarPorIdClienteYClave(int idCliente, int claveTarjeta) {
        MetodoPago metodoPago = almacenamiento.get(idCliente);
        if (metodoPago != null && metodoPago.getClaveTarjeta() == claveTarjeta) {
            return Optional.of(metodoPago);
        }
        return Optional.empty();
    }

    @Override
    public List<MetodoPago> listarTodos() {
        return new ArrayList<>(almacenamiento.values());
    }

    @Override
    public void actualizar(int idCliente, MetodoPago metodoPago) {
        if (!almacenamiento.containsKey(idCliente)) {
            throw new RuntimeException("No se encontró el método de pago para el cliente ID: " + idCliente);
        }
        almacenamiento.put(idCliente, metodoPago);
    }

    @Override
    public void actualizar(int idCliente, MetodoPago metodoPago, Connection connection) {
        actualizar(idCliente, metodoPago);
    }

    @Override
    public void eliminar(int idCliente) {
        almacenamiento.remove(idCliente);
    }
}
