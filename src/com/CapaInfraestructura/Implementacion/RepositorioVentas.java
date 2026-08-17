package com.CapaInfraestructura.Implementacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.Venta;

public class RepositorioVentas implements IVentaRepository {

private static final List<Venta> ventas = new CopyOnWriteArrayList<>();

private static int maximoId() {
    if (ventas.isEmpty()) {
        return 1;
    }
    int maxId = 0;
    for (Venta venta : ventas) {
        if (venta.getId() > maxId) {
            maxId = venta.getId();
        }
    }
    return maxId + 1;
}

private static final AtomicInteger siguienteId = new AtomicInteger(maximoId());

    public synchronized void guardar(Venta venta) {
        if (venta == null) {
            throw new IllegalArgumentException("La venta no puede ser nula.");
        }
        if (venta.getId() == 0) {
            siguienteId.accumulateAndGet(maximoId(), Math::max);
            venta.setId(siguienteId.getAndIncrement());
        }
        ventas.add(venta);
    }


    public Optional<Venta> buscarPorId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < ventas.size(); i++) {
            Venta ventaActual = ventas.get(i);
            if (ventaActual.getId() == id) {
                return Optional.of(ventaActual);
            }
        }
        return Optional.empty();
    }

    
    public List<Venta> listarTodas() {
        return new ArrayList<>(ventas);
    }


    public void actualizar(int id, Venta venta) {
        if (venta == null) {
            throw new IllegalArgumentException("La venta no puede ser nula.");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < ventas.size(); i++) {
            if (ventas.get(i).getId() == id) {
                ventas.set(i, venta);
                return;
            }
        }
    }
}
