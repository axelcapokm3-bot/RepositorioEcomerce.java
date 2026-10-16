package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.VinculoCuentaNegocio;

import java.util.List;
import java.util.Optional;

public interface ICuentaNegocioRepositorio {
    void guardar(VinculoCuentaNegocio vinculo);

    Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(int idCuenta, long idNegocio);

    List<VinculoCuentaNegocio> listarPorCuenta(int idCuenta);

    boolean existeVinculo(int idCuenta, long idNegocio);
}
