package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.IServiceCliente;
import com.CapaAplicacion.Mapper.ClienteMapper;
import com.CapaDominio.Entidades.Cliente;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ClienteService implements IServiceCliente {

    private final IClienteRepositorio repository;


    public ClienteService(IClienteRepositorio repository) {
        this.repository = repository;
    }

@Override
public CompletableFuture<Void> guardarAsync(final ClienteRequestDTO dto) {
    return CompletableFuture.runAsync(new Runnable() {
        @Override
        public void run() {
            Cliente cliente = ClienteMapper.toEntidad(0, dto);
            repository.guardar(cliente);
        }
    });
}

    @Override
    public CompletableFuture<Optional<ClienteResponseDTO>> buscarPorIdAsync(final int id) {
        return CompletableFuture.supplyAsync(new Supplier<Optional<ClienteResponseDTO>>() {
            @Override
            public Optional<ClienteResponseDTO> get() {
                Optional<Cliente> opt = repository.buscarPorId(id);
                if (opt.isPresent()) {
                    return Optional.of(ClienteMapper.toResponseDTO(opt.get()));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync() {
        return CompletableFuture.supplyAsync(new Supplier<List<ClienteResponseDTO>>() {
            @Override
            public List<ClienteResponseDTO> get() {
                List<Cliente> listaEntidades = repository.listarTodos();
                List<ClienteResponseDTO> listaDTOs = new ArrayList<>();
                for (Cliente c : listaEntidades) {
                    listaDTOs.add(ClienteMapper.toResponseDTO(c));
                }
                return listaDTOs;
            }
        });
    }

    @Override
    public CompletableFuture<Void> actualizarAsync(final int id, final ClienteRequestDTO dto) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                repository.buscarPorId(id)
                        .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
                
                Cliente clienteActualizado = ClienteMapper.toEntidad(id, dto);
                repository.actualizar(id, clienteActualizado);
            }
        });
    }

    @Override
    public CompletableFuture<Void> eliminarAsync(final int id) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                repository.eliminar(id);
            }
        });
    }
}
