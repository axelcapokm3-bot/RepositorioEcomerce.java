package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cliente;
import java.util.List;
import java.util.Optional;

public interface IClienteRepositorio {
    // CREATE
    void guardar(Cliente cliente);

    // READ
    Optional<Cliente> buscarPorId(long idNegocio, int id);
    List<Cliente> listarTodos(long idNegocio);
 

    // UPDATE
    void actualizar(long idNegocio, int id, Cliente cliente);

    // DELETE
    void eliminar(long idNegocio, int id);
}
