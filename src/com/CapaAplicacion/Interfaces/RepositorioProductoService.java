package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RepositorioProductoService {
    CompletableFuture<Void> guardarAsync(long idNegocio, ProductoRequestDTO producto);

    CompletableFuture<Optional<ProductoResponseDTO>> buscarPorIdAsync(long idNegocio, int id);

    CompletableFuture<Optional<ProductoResponseDTO>> buscarActivoPorIdAsync(long idNegocio, int id);

    CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync(long idNegocio);
    CompletableFuture<List<ProductoResponseDTO>> listarTodosAsync(long idNegocio, int limite, int offset);
    CompletableFuture<Long> contarTotalAsync(long idNegocio);

    CompletableFuture<List<ProductoResponseDTO>> listarActivosAsync(long idNegocio);
    CompletableFuture<List<ProductoResponseDTO>> listarActivosAsync(long idNegocio, int limite, int offset);
    CompletableFuture<Long> contarActivosAsync(long idNegocio);

    CompletableFuture<Void> actualizarAsync(long idNegocio, int id, ProductoRequestDTO producto);

    CompletableFuture<Void> eliminarAsync(long idNegocio, int id);
}