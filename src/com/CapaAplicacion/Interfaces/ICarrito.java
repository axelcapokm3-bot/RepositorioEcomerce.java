package com.CapaAplicacion.Interfaces;
import com.CapaDominio.Entidades.Carrito;
import java.util.Optional;

public interface ICarrito extends ICarritoRepository {
    default Optional<Carrito> ObtenenerPorId(int idCliente) {
        return obtenerPorClienteId(idCliente);
    }
}
