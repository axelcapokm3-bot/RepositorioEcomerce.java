package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Producto;
import java.sql.Connection;
import  java.util.List;
import java.util.Optional;

public interface IProductoRepository {
    // CREATE
   public void guardar(Producto producto);

    // READ
    public Optional<Producto> buscarPorId(long idNegocio, int id);
  public  List<Producto> listarTodos(long idNegocio);

    // UPDATE
    public void actualizar(long idNegocio, int id ,Producto producto);

      default void actualizar(long idNegocio, int id, Producto producto, Connection connection) throws java.sql.SQLException {
        actualizar(idNegocio, id, producto);
      }

    // DELETE
  public  void eliminar(long idNegocio, int id);
}