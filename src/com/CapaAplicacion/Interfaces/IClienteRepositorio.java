package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cliente;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface IClienteRepositorio {
    // CREATE
    void guardar(Cliente cliente);

    // READ
    Optional<Cliente> buscarPorId(long idNegocio, int id);
    Optional<Cliente> buscarPorDni(long idNegocio, String dni);
    List<Cliente> listarTodos(long idNegocio);
 

    // UPDATE
    void actualizar(long idNegocio, int id, Cliente cliente);

    // DELETE
    void eliminar(long idNegocio, int id);

    default Optional<Cliente> buscarPorDniParaActualizar(Connection conexion, long idNegocio, String dni) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }

    default void guardar(Connection conexion, Cliente cliente) {
        throw new UnsupportedOperationException("El repositorio no admite operaciones transaccionales.");
    }
}
