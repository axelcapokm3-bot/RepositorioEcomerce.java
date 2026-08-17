package com.CapaAplicacion.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.mindrot.jbcrypt.BCrypt;

import com.CapaAplicacion.DTOentrada.CuentaRequestDTO;
import com.CapaAplicacion.DTOsalida.CuentaResponseDTO;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.IServiceCuenta;
import com.CapaAplicacion.Mapper.CuentaMapper;
import com.CapaAplicacion.Utilidades.OfuscadorDeContraseñas;
import com.CapaDominio.Entidades.Cuenta;

public class CuentaService implements IServiceCuenta {

    private final ICuentaRepositorio repository;

    private static final String PEPPER = "EcommerseStrictPepper2026";

    public CuentaService(ICuentaRepositorio repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<List<CuentaResponseDTO>> listarTodosAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<Cuenta> listaEntidades = repository.listarTodos();
            List<CuentaResponseDTO> listaDTOs = new ArrayList<>();
            for (Cuenta c : listaEntidades) {
                listaDTOs.add(CuentaMapper.deEntidadAResponseDto(c));
            }
            return listaDTOs;
        });
    }

    @Override
    public CompletableFuture<Void> guardarAsync(final CuentaRequestDTO dto) {
        return CompletableFuture.runAsync(() -> {
            Optional<Cuenta> cuentaExistente = repository.buscarPorCorreo(dto.correoElectronico());

            if (dto.contraseniaPlana() == null || dto.contraseniaPlana().length() < 6) {
                throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
            }

            if (cuentaExistente.isPresent()) {
                throw new IllegalArgumentException(
                        "No se puede crear la cuenta: El correo electrónico '" + dto.correoElectronico() + "' ya está registrado."
                );
            }

            String passOfuscada = OfuscadorDeContraseñas.ofuscadorEstricto(dto.contraseniaPlana(), PEPPER);
            String hashFinal = BCrypt.hashpw(passOfuscada, BCrypt.gensalt());

            CuentaRequestDTO dtoConHash = new CuentaRequestDTO(
                    dto.nombreCuenta(),
                    dto.correoElectronico(),
                    hashFinal,
                    dto.idCliente(),
                    dto.rolUsuario(),
                    dto.saldo()
            );

            Cuenta cuenta = CuentaMapper.deRegistroDtoAEntidad(0, dtoConHash);
            repository.guardar(cuenta);
        });
    }

    @Override
    public CompletableFuture<Optional<CuentaResponseDTO>> buscarPorIdAsync(final int id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Cuenta> opt = repository.buscarPorId(id);
            if (opt.isPresent()) {
                return Optional.of(CuentaMapper.deEntidadAResponseDto(opt.get()));
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<CuentaResponseDTO>> buscarPorCorreoAsync(final String correo) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Cuenta> opt = repository.buscarPorCorreo(correo);
            if (opt.isPresent()) {
                return Optional.of(CuentaMapper.deEntidadAResponseDto(opt.get()));
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> actualizarAsync(final int id, final CuentaRequestDTO dto) {
        return CompletableFuture.runAsync(() -> {
            Cuenta cuentaActual = repository.buscarPorId(id)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));

            Optional<Cuenta> correoOcupado = repository.buscarPorCorreo(dto.correoElectronico());

            if (correoOcupado.isPresent() && correoOcupado.get().getId() != id) {
                throw new IllegalArgumentException("El correo electrónico ya está registrado por otro usuario.");
            }

            String hashFinal;

            if (dto.contraseniaPlana() != null && !dto.contraseniaPlana().trim().isEmpty()) {
                if (dto.contraseniaPlana().length() < 6) {
                    throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
                }
                String passOfuscada = OfuscadorDeContraseñas.ofuscadorEstricto(dto.contraseniaPlana(), PEPPER);
                hashFinal = BCrypt.hashpw(passOfuscada, BCrypt.gensalt());
            } else {
                hashFinal = cuentaActual.getContrasenia();
            }

            CuentaRequestDTO dtoConHash = new CuentaRequestDTO(
                    dto.nombreCuenta(),
                    dto.correoElectronico(),
                    hashFinal,
                    dto.idCliente(),
                    dto.rolUsuario(),
                    dto.saldo()
            );

            Cuenta cuentaActualizada = CuentaMapper.deRegistroDtoAEntidad(id, dtoConHash);
            repository.actualizar(id, cuentaActualizada);
        });
    }

    @Override
    public CompletableFuture<Void> eliminarAsync(final int id) {
        return CompletableFuture.runAsync(() -> repository.eliminar(id));
    }

    public CompletableFuture<Void> transferirAsync(final int idCuentaOrigen, final int idCuentaDestino, final double monto) {
        return CompletableFuture.runAsync(() -> {
            if (monto <= 0) {
                throw new IllegalArgumentException("El monto a transferir debe ser mayor a cero.");
            }

            Cuenta cuentaOrigen = repository.buscarPorId(idCuentaOrigen)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La cuenta de origen con ID " + idCuentaOrigen + " no existe."
                    ));

            Cuenta cuentaDestino = repository.buscarPorId(idCuentaDestino)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La cuenta de destino con ID " + idCuentaDestino + " no existe."
                    ));

            if (idCuentaOrigen == idCuentaDestino) {
                throw new IllegalArgumentException("No se puede realizar una transferencia a la misma cuenta de origen.");
            }

            cuentaOrigen.transferirA(cuentaDestino, BigDecimal.valueOf(monto));

            repository.actualizar(idCuentaOrigen, cuentaOrigen);
            repository.actualizar(idCuentaDestino, cuentaDestino);
        });
    }
}