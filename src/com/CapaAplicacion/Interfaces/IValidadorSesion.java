package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.RolUsuario;

public interface IValidadorSesion {
    boolean estaVigente(long idCuenta, int idCliente, long idNegocio, RolUsuario rol);
}
