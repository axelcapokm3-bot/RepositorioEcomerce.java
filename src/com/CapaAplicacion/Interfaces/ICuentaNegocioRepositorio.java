package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.VinculoCuentaNegocio;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface ICuentaNegocioRepositorio {
    void guardar(VinculoCuentaNegocio vinculo);

    Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(long idCuenta, long idNegocio);

    List<VinculoCuentaNegocio> listarPorCuenta(long idCuenta);

    boolean existeVinculo(long idCuenta, long idNegocio);

    default Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(Connection conexion, long idCuenta, long idNegocio) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }

    default Optional<VinculoCuentaNegocio> buscarPorNegocioYCliente(Connection conexion, long idNegocio, int idCliente) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }

    default void guardar(Connection conexion, VinculoCuentaNegocio vinculo) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }
}
