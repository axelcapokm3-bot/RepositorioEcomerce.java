package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Producto;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface IProductoRepository {
    // CREATE
   public void guardar(Producto producto);

    // READ
    public Optional<Producto> buscarPorId(long idNegocio, int id);
  public  List<Producto> listarTodos(long idNegocio);

    default List<Producto> listarActivos(long idNegocio) {
        List<Producto> activos = new ArrayList<>();
        for (Producto producto : listarTodos(idNegocio)) {
            if (producto.puedeVenderse()) {
                activos.add(producto);
            }
        }
        return activos;
    }

    default Optional<Producto> buscarPorIdParaUpdate(long idNegocio, int id, Connection connection)
            throws java.sql.SQLException {
        return buscarPorId(idNegocio, id);
    }

    // UPDATE
    public void actualizar(long idNegocio, int id ,Producto producto);

      default void actualizar(long idNegocio, int id, Producto producto, Connection connection) throws java.sql.SQLException {
        actualizar(idNegocio, id, producto);
      }

    // DELETE
  public  void eliminar(long idNegocio, int id);
}