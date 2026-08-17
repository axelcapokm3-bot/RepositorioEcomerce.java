package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Cuenta;
import java.util.List;
import java.util.Optional;

public interface ICuentaRepositorio {
    // CREATE
    void guardar(Cuenta cuenta);

    // READ
    Optional<Cuenta> buscarPorId(int id);
    List<Cuenta> listarTodos();
    Optional<Cuenta> buscarPorCorreo(String correo);
    List<Cuenta> buscarPorIdCliente(int idCliente);

    // UPDATE
    void actualizar(int id, Cuenta cuenta);

    // DELETE
    void eliminar(int id);
}
