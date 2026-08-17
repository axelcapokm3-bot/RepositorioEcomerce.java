package com.CapaAplicacion.Controller;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaAplicacion.Interfaces.RepositorioProductoService;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProductoController {

    private final RepositorioProductoService servicio;

    public ProductoController(RepositorioProductoService servicio) {
        this.servicio = servicio;
    }

    public CompletableFuture<List<ProductoResponseDTO>> listarTodos() {
        return servicio.listarTodosAsync();
    }

    public CompletableFuture<Void> crear(ProductoRequestDTO producto) {
        return servicio.guardarAsync(producto);
    }

    public CompletableFuture<Void> actualizar(int id, ProductoRequestDTO producto) {
        return servicio.actualizarAsync(id, producto);
    }

    public CompletableFuture<Void> eliminar(int id) {
        return servicio.eliminarAsync(id);
    }
}