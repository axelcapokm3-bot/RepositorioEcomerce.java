package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaDominio.Entidades.Cliente;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RepositorioCliente implements IClienteRepositorio {

private static final List<Cliente> clientes = new CopyOnWriteArrayList<>();

private static int maximoId() {
    if (clientes.isEmpty()) {
        return 1;
    }
    int maxId = 0;
    for (Cliente cliente : clientes) {
        if (cliente.getId() > maxId) {
            maxId = cliente.getId();
        }
    }
    return maxId + 1;
}

private static final AtomicInteger siguienteId = new AtomicInteger(maximoId());

    @Override
    public void guardar(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (cliente.getId() == 0) {
            siguienteId.accumulateAndGet(maximoId(), Math::max);
            cliente.setId(siguienteId.getAndIncrement());
        }
        clientes.add(cliente);
    }



    @Override
    public List<Cliente> listarTodos() {
        return new ArrayList<>(clientes);
    }

    @Override
    public Optional<Cliente> buscarPorId(int id ) {
                for(int i = 0 ; i < clientes.size() ; i ++ ) { 
   Cliente clienteActual = clientes.get(i);
        
        if (clienteActual.getId() == id) { 
            return Optional.of(clienteActual);
        }
    }
    return Optional.empty(); 
}

    @Override
    public void actualizar(int id, Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }

        for (int i = 0; i < clientes.size(); i++) {
            if (clientes.get(i).getId() == id) {
                clientes.set(i, cliente);
                return;
            }
        }
    }

    @Override
    public void eliminar(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < clientes.size(); i++) {
            if (clientes.get(i).getId() == id) {
                clientes.remove(i);
                return;
            }
        }
    }
}
