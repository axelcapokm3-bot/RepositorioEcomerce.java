package com.CapaInfraestructura.Implementacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaDominio.Entidades.Cuenta;

public class RepositorioCuenta implements ICuentaRepositorio {

    private static final Map<Integer, Cuenta> cuentas = new ConcurrentHashMap<>();
    private static final AtomicInteger siguienteId = new AtomicInteger(1);

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta == null) {
            throw new IllegalArgumentException("La cuenta no puede ser nula.");
        }
        if (cuenta.getId() == 0) {
            cuenta.setId(siguienteId.getAndIncrement());
        }
        cuentas.put(cuenta.getId(), cuenta);
    }

    @Override
    public Optional<Cuenta> buscarPorId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }
        return Optional.ofNullable(cuentas.get(id));
    }

    @Override
    public List<Cuenta> listarTodos() {
        return new ArrayList<>(cuentas.values());
    }

    @Override
    public Optional<Cuenta> buscarPorCorreo(String correo) {
        if (correo == null || correo.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo no puede estar vacío.");
        }


        return cuentas.values().stream()
                .filter(c -> c.getCorreoElectronico() != null && c.getCorreoElectronico().equalsIgnoreCase(correo))
                .findFirst();
    }

 
    @Override
    public List<Cuenta> buscarPorIdCliente(int idCliente) {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID del cliente debe ser mayor a cero.");
        }

        return cuentas.values().stream()
                .filter(c -> c.getIdCliente() == idCliente)
                .collect(Collectors.toList());
    }

    @Override
    public void actualizar(int id, Cuenta cuenta) {
        if (cuenta == null) {
            throw new IllegalArgumentException("La cuenta no puede ser nula.");
        }
        if (!cuentas.containsKey(id)) {
            throw new IllegalArgumentException("No existe una cuenta con el ID recibido: " + id);
        }
        cuentas.put(id, cuenta);
    }

    @Override
    public void eliminar(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }
        cuentas.remove(id);
    }
}