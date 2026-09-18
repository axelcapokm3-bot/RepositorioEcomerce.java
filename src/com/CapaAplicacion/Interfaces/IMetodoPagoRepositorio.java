package com.CapaAplicacion.Interfaces;

import java.util.List;
import java.util.Optional;

import com.CapaDominio.Entidades.MetodoPago;
import java.sql.Connection;

public interface IMetodoPagoRepositorio {
    void guardar(MetodoPago metodoPago);
    Optional<MetodoPago> buscarPorIdCliente(int idCliente);
    List<MetodoPago> listarTodos();
    void actualizar(int idCliente, MetodoPago metodoPago);
    default void actualizar(int idCliente, MetodoPago metodoPago, Connection connection) {
        actualizar(idCliente, metodoPago);
    }
    void eliminar(int idCliente);
}
