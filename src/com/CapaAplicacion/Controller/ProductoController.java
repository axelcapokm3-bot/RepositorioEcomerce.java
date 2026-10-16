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

    public CompletableFuture<List<ProductoResponseDTO>> listarTodos(long idNegocio) {
        return servicio.listarTodosAsync(idNegocio);
    }

    public CompletableFuture<Void> crear(long idNegocio, ProductoRequestDTO producto) {
        return servicio.guardarAsync(idNegocio, producto);
    }

    public CompletableFuture<Void> actualizar(long idNegocio, int id, ProductoRequestDTO producto) {
        return servicio.actualizarAsync(idNegocio, id, producto);
    }

    public CompletableFuture<Void> eliminar(long idNegocio, int id) {
        return servicio.eliminarAsync(idNegocio, id);
    }
}