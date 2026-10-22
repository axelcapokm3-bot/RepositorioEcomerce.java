package com.CapaAplicacion.Interfaces;

import java.util.Optional;

public interface ICarritoRepository {
    Optional<CarritoPersistido> obtener(long idNegocio, int idCliente);

    CarritoPersistido agregarItem(long idNegocio, int idCliente, int idProducto,
                                  int cantidad, int maxCantidad);

    CarritoPersistido eliminarItem(long idNegocio, int idCliente, int idProducto);

    void eliminar(long idNegocio, int idCliente);
}
