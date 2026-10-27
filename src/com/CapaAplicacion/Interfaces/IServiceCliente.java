package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IServiceCliente {
    CompletableFuture<Void> guardarAsync(long idNegocio, ClienteRequestDTO clienteRequestDTO);

    CompletableFuture<Optional<ClienteResponseDTO>> buscarPorIdAsync(long idNegocio, int id);

    CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync(long idNegocio);
    CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync(long idNegocio, int limite, int offset);
    CompletableFuture<Long> contarTotalAsync(long idNegocio);

    CompletableFuture<Void> actualizarAsync(long idNegocio, int id, ClienteRequestDTO clienteRequestDTO);

    CompletableFuture<Void> eliminarAsync(long idNegocio, int id);
}
