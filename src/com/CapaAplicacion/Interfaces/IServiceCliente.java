package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IServiceCliente {
    CompletableFuture<Void> guardarAsync(ClienteRequestDTO clienteRequestDTO);

    CompletableFuture<Optional<ClienteResponseDTO>> buscarPorIdAsync(int id);

    CompletableFuture<List<ClienteResponseDTO>> listarTodosAsync();



    CompletableFuture<Void> actualizarAsync(int id, ClienteRequestDTO clienteRequestDTO);

    CompletableFuture<Void> eliminarAsync(int id);
}
