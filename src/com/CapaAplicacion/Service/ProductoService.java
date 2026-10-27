package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaAplicacion.Interfaces.*;

import com.CapaAplicacion.Mapper.ProductoMapper;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaDominio.Entidades.*;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ProductoService implements RepositorioProductoService {

    private final IProductoRepository repository;
    private final EventBus eventBus;
    private final IServiceExecutor executor;

    public ProductoService(IProductoRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.executor = RegistroExecutors.obtener(TipoServicio.PRODUCTO);
    }

    @Override
    public CompletableFuture<Void> guardarAsync(final long idNegocio, final ProductoRequestDTO dto) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                Producto producto = new Producto(dto.nombre(), dto.precio(), dto.stock(), 0, 0, dto.categoria());
                producto.setIdNegocio(idNegocio);
                repository.guardar(producto);
                eventBus.publicar(new ProductosEventos(producto.getId(), dto));
            }
        });
    }

    @Override
    public CompletableFuture<Optional<ProductoResponseDTO>> buscarPorIdAsync(final long idNegocio, final int id) {
        return buscarAsync(idNegocio, id, false);
    }

    @Override
    public CompletableFuture<Optional<ProductoResponseDTO>> buscarActivoPorIdAsync(
            final long idNegocio, final int id) {
        return buscarAsync(idNegocio, id, true);
    }

    private CompletableFuture<Optional<ProductoResponseDTO>> buscarAsync(
            final long idNegocio, final int id, final boolean soloVendibles) {
        return executor.ejecutar(new java.util.concurrent.Callable<Optional<ProductoResponseDTO>>() {
            @Override
            public Optional<ProductoResponseDTO> call() {
                Optional<Producto> opt = repository.buscarPorId(idNegocio, id);
                if (opt.isPresent() && (!soloVendibles || opt.get().puedeVenderse())) {
                    return Optional.of(ProductoMapper.toResponseDTO(opt.get()));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync(final long idNegocio) {
        return listarAsync(idNegocio, false, -1, -1);
    }

    @Override
    public CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync(final long idNegocio, int limite, int offset) {
        return listarAsync(idNegocio, false, limite, offset);
    }

    @Override
    public CompletableFuture<List<ProductoResponseDTO>> listarActivosAsync(final long idNegocio) {
        return listarAsync(idNegocio, true, -1, -1);
    }

    @Override
    public CompletableFuture<List<ProductoResponseDTO>> listarActivosAsync(final long idNegocio, int limite, int offset) {
        return listarAsync(idNegocio, true, limite, offset);
    }

    private CompletableFuture<List<ProductoResponseDTO>> listarAsync(
            final long idNegocio, final boolean soloActivos, final int limite, final int offset) {
        return executor.ejecutar(new java.util.concurrent.Callable<List<ProductoResponseDTO>>() {
            @Override
            public List<ProductoResponseDTO> call() {
                List<Producto> listaEntidades;
                if (limite > 0) {
                     listaEntidades = soloActivos
                        ? repository.listarActivosPaginado(idNegocio, limite, offset)
                        : repository.listarPaginado(idNegocio, limite, offset);
                } else {
                     listaEntidades = soloActivos
                        ? repository.listarActivos(idNegocio)
                        : repository.listarTodos(idNegocio);
                }
                
                List<ProductoResponseDTO> listaDTOs = new ArrayList<>();
                for (Producto p : listaEntidades) {
                    listaDTOs.add(ProductoMapper.toResponseDTO(p));
                }
                return listaDTOs;
            }
        });
    }

    @Override
    public CompletableFuture<Void> actualizarAsync(final long idNegocio, final int id, final ProductoRequestDTO dto) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                Producto producto = repository.buscarPorId(idNegocio, id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado."));

                producto.setNombre(dto.nombre());
                producto.actualizarPrecio(dto.precio());
                producto.setStock(dto.stock());

                repository.actualizar(idNegocio, id, producto);
            }
        });
    }

    @Override
    public CompletableFuture<Void> eliminarAsync(final long idNegocio, final int id) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                repository.buscarPorId(idNegocio, id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado."));
                repository.eliminar(idNegocio, id);
            }
        });
    }

    @Override
    public CompletableFuture<Long> contarTotalAsync(final long idNegocio) {
        return executor.ejecutar(() -> repository.contarTotal(idNegocio));
    }

    @Override
    public CompletableFuture<Long> contarActivosAsync(final long idNegocio) {
        return executor.ejecutar(() -> repository.contarActivos(idNegocio));
    }


}
