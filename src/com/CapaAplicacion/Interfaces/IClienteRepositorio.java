package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cliente;
import java.util.List;
import java.util.Optional;

public interface IClienteRepositorio {
    // CREATE
    void guardar(Cliente cliente);

    // READ
    Optional<Cliente> buscarPorId(int id);
    List<Cliente> listarTodos();
 

    // UPDATE
    void actualizar(int id, Cliente cliente);

    // DELETE
    void eliminar(int id);
}
