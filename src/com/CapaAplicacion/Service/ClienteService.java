package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.IServiceCliente;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Mapper.ClienteMapper;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ClienteService implements IServiceCliente {

    private final IClienteRepositorio repository;
    private final IServiceExecutor executor;


    public ClienteService(IClienteRepositorio repository) {
        this.repository = repository;
        this.executor = RegistroExecutors.obtener(TipoServicio.CLIENTE);
    }

@Override
public CompletableFuture<Void> guardarAsync(final long idNegocio, final ClienteRequestDTO dto) {
    return executor.ejecutar(new Runnable() {
        @Override
        public void run() {
            Cliente cliente = ClienteMapper.toEntidad(0, dto);
            cliente.setIdNegocio(idNegocio);
            repository.guardar(cliente);
        }
    });
}

    @Override
    public CompletableFuture<Optional<ClienteResponseDTO>> buscarPorIdAsync(final long idNegocio, final int id) {
        return executor.ejecutar(new java.util.concurrent.Callable<Optional<ClienteResponseDTO>>() {
            @Override
            public Optional<ClienteResponseDTO> call() {
                Optional<Cliente> opt = repository.buscarPorId(idNegocio, id);
                if (opt.isPresent()) {
                    return Optional.of(ClienteMapper.toResponseDTO(opt.get()));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync(final long idNegocio) {
        return listarTodosAsync(idNegocio, -1, -1);
    }

    @Override
    public CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync(final long idNegocio, final int limite, final int offset) {
        return executor.ejecutar(new java.util.concurrent.Callable<List<ClienteResponseDTO>>() {
            @Override
            public List<ClienteResponseDTO> call() {
                List<Cliente> listaEntidades = (limite > 0) 
                        ? repository.listarPaginado(idNegocio, limite, offset)
                        : repository.listarTodos(idNegocio);
                List<ClienteResponseDTO> listaDTOs = new ArrayList<>();
                for (Cliente c : listaEntidades) {
                    listaDTOs.add(ClienteMapper.toResponseDTO(c));
                }
                return listaDTOs;
            }
        });
    }

    @Override
    public CompletableFuture<Long> contarTotalAsync(final long idNegocio) {
        return executor.ejecutar(() -> repository.contarTotal(idNegocio));
    }

    @Override
    public CompletableFuture<Void> actualizarAsync(final long idNegocio, final int id, final ClienteRequestDTO dto) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                exigirClienteDelNegocio(idNegocio, id);
                Cliente clienteActualizado = ClienteMapper.toEntidad(id, dto);
                clienteActualizado.setIdNegocio(idNegocio);
                repository.actualizar(idNegocio, id, clienteActualizado);
            }
        });
    }

    @Override
    public CompletableFuture<Void> eliminarAsync(final long idNegocio, final int id) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                exigirClienteDelNegocio(idNegocio, id);
                repository.eliminar(idNegocio, id);
            }
        });
    }

    private void exigirClienteDelNegocio(long idNegocio, int id) {
        if (repository.buscarPorId(idNegocio, id).isEmpty()) {
            throw new RecursoNoEncontradoException("Cliente no encontrado.");
        }
    }
}
