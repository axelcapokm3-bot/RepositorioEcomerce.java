package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Carrito;
import java.util.Optional;

public interface ICarritoRepository {
    Optional<Carrito> obtenerPorClienteId(int idCliente);

    void guardar(Carrito carrito);

    void eliminarPorClienteId(int idCliente);
}
