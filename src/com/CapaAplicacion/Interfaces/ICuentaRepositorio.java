package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cuenta;

import java.util.Optional;

public interface ICuentaRepositorio {
    void guardar(Cuenta cuenta);

    Optional<Cuenta> buscarPorId(int id);

    Optional<Cuenta> buscarPorCorreo(String correoElectronico);

    boolean existeCorreo(String correoElectronico);

    long contarCuentas();
}
