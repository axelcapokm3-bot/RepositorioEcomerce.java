package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.Seguridad.Principal;

import java.util.Optional;

public interface IProveedorTokenAcceso {
    String emitir(long idCuenta, int idCliente, long idNegocio, String rol);

    Optional<Principal> validar(String token);

    void revocar(String token);

    long segundosDeVida();
}
