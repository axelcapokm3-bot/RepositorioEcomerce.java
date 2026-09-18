package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaAplicacion.Interfaces.*;

import com.CapaAplicacion.Mapper.ProductoMapper;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaDominio.Entidades.*;
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
    public CompletableFuture<Void> guardarAsync(final ProductoRequestDTO dto) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                Producto producto = new Producto(dto.nombre(), dto.precio(), dto.stock(), 0, 0, dto.categoria());
                repository.guardar(producto);
                eventBus.publicar(new ProductosEventos(producto.getId(), dto));
            }
        });
    }

    @Override
    public CompletableFuture<Optional<ProductoResponseDTO>> buscarPorIdAsync(final int id) {
        return executor.ejecutar(new java.util.concurrent.Callable<Optional<ProductoResponseDTO>>() {
            @Override
            public Optional<ProductoResponseDTO> call() {
                Optional<Producto> opt = repository.buscarPorId(id);
                if (opt.isPresent()) {
                    return Optional.of(ProductoMapper.toResponseDTO(opt.get()));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync() {
        return executor.ejecutar(new java.util.concurrent.Callable<List<ProductoResponseDTO>>() {
            @Override
            public List<ProductoResponseDTO> call() {
                List<Producto> listaEntidades = repository.listarTodos();
                List<ProductoResponseDTO> listaDTOs = new ArrayList<>();
                for (Producto p : listaEntidades) {
                    listaDTOs.add(ProductoMapper.toResponseDTO(p));
                }
                return listaDTOs;
            }
        });
    }

    @Override
    public CompletableFuture<Void> actualizarAsync(final int id, final ProductoRequestDTO dto) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                Producto producto = repository.buscarPorId(id)
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
                
                producto.setNombre(dto.nombre());
                producto.actualizarPrecio(dto.precio());
                producto.setStock(dto.stock());
                
                repository.actualizar(id, producto);
            }
        });
    }

    @Override
    public CompletableFuture<Void> eliminarAsync(final int id) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                repository.eliminar(id);
            }
        });
    }


}
