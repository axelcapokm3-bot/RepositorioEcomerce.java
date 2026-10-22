package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cuenta;

import java.sql.Connection;
import java.util.Optional;

public interface ICuentaRepositorio {
    void guardar(Cuenta cuenta);

    Optional<Cuenta> buscarPorId(long id);

    Optional<Cuenta> buscarPorCorreo(String correoElectronico);

    boolean existeCorreo(String correoElectronico);

    long contarCuentas();

    default Optional<Cuenta> buscarPorCorreo(Connection conexion, String correoElectronico) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }

    default void guardar(Connection conexion, Cuenta cuenta) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }
}
