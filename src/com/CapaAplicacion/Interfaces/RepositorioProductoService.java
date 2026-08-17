package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RepositorioProductoService {
    CompletableFuture<Void> guardarAsync(ProductoRequestDTO producto);

    CompletableFuture<Optional<ProductoResponseDTO>> buscarPorIdAsync(int id);

    CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync();

    CompletableFuture<Void> actualizarAsync(int id, ProductoRequestDTO producto);

    CompletableFuture<Void> eliminarAsync(int id);
}