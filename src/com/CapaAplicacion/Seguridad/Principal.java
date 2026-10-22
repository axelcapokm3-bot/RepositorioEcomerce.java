package com.CapaAplicacion.Seguridad;

import com.CapaDominio.Entidades.RolUsuario;

public record Principal(
        long idCuenta,
        int idCliente,
        long idNegocio,
        RolUsuario rol
) {
    public Principal {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta del principal no es válido.");
        }
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID de cliente del principal no es válido.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio del principal no es válido.");
        }
        if (rol == null) {
            throw new IllegalArgumentException("El rol del principal es obligatorio.");
        }
    }

    public boolean esAdmin() {
        return rol == RolUsuario.ADMIN;
    }
}
