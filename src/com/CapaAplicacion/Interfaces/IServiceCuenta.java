package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOentrada.CuentaRequestDTO;
import com.CapaAplicacion.DTOsalida.CuentaResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IServiceCuenta {
    CompletableFuture<Void> guardarAsync(CuentaRequestDTO cuentaRequestDTO);

    CompletableFuture<Optional<CuentaResponseDTO>> buscarPorIdAsync(int id);

    CompletableFuture<List<CuentaResponseDTO>> listarTodosAsync();

    CompletableFuture<Optional<CuentaResponseDTO>> buscarPorCorreoAsync(String correo);

    CompletableFuture<Void> actualizarAsync(int id, CuentaRequestDTO cuentaRequestDTO);

    CompletableFuture<Void> eliminarAsync(int id);
}
