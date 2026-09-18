package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Producto;
import java.sql.Connection;
import  java.util.List;
import java.util.Optional;

public interface IProductoRepository {
    // CREATE
   public void guardar(Producto producto);

    // READ
    public Optional<Producto> buscarPorId(int id);
  public  List<Producto> listarTodos();

    // UPDATE
   public void actualizar(int id ,Producto producto);

     default void actualizar(int id, Producto producto, Connection connection) throws java.sql.SQLException {
       actualizar(id, producto);
     }

    // DELETE
  public  void eliminar(int id);
}