package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.Producto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProductoRepositorio implements IProductoRepository {

    private static final List<Producto> productos = new CopyOnWriteArrayList<>();


    public void guardar(Producto producto) {
        if (producto == null) {
            throw new IllegalArgumentException("El producto no puede ser nulo.");
        }
        productos.add(producto);
    }


    public Optional<Producto> buscarPorId(long idNegocio, int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < productos.size(); i++) {
            Producto productoActual = productos.get(i);
            if (productoActual.getId() == id && productoActual.getIdNegocio() == idNegocio) {
                return Optional.of(productoActual);
            }
        }
        return Optional.empty();
    }


    public List<Producto> listarTodos(long idNegocio) {
        List<Producto> filtrados = new ArrayList<>();
        for (Producto producto : productos) {
            if (producto.getIdNegocio() == idNegocio) {
                filtrados.add(producto);
            }
        }
        return filtrados;
    }

   
    public void actualizar(long idNegocio, int id, Producto producto) {
        if (producto == null) {
            throw new IllegalArgumentException("El producto no puede ser nulo.");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < productos.size(); i++) {
            if (productos.get(i).getId() == id && productos.get(i).getIdNegocio() == idNegocio) {
                productos.set(i, producto);
                return;
            }
        }
    }

    public void eliminar(long idNegocio, int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
        }

        for (int i = 0; i < productos.size(); i++) {
            if (productos.get(i).getId() == id && productos.get(i).getIdNegocio() == idNegocio) {
                productos.remove(i);
                return;
            }
        }
    }
}