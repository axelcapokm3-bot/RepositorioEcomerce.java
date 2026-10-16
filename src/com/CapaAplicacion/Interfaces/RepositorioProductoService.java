package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RepositorioProductoService {
    CompletableFuture<Void> guardarAsync(long idNegocio, ProductoRequestDTO producto);

    CompletableFuture<Optional<ProductoResponseDTO>> buscarPorIdAsync(long idNegocio, int id);

    CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync(long idNegocio);

    CompletableFuture<Void> actualizarAsync(long idNegocio, int id, ProductoRequestDTO producto);

    CompletableFuture<Void> eliminarAsync(long idNegocio, int id);
}