package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.CarritoPersistido;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class CarritoService {
    private final ICarritoRepository carritoRepository;
    private final IProductoRepository productoRepository;
    private final INegocioRepository negocioRepository;
    private final IServiceExecutor executor;

    public CarritoService(ICarritoRepository carritoRepository,
                          IProductoRepository productoRepository) {
        this(carritoRepository, productoRepository, null);
    }

    public CarritoService(ICarritoRepository carritoRepository,
                          IProductoRepository productoRepository,
                          INegocioRepository negocioRepository) {
        if (carritoRepository == null || productoRepository == null) {
            throw new IllegalArgumentException("El repositorio de carrito y el de productos son obligatorios.");
        }
        this.carritoRepository = carritoRepository;
        this.productoRepository = productoRepository;
        this.negocioRepository = negocioRepository;
        this.executor = RegistroExecutors.obtener(TipoServicio.VENTA);
    }

    public CompletableFuture<Carrito> consultarAsync(final long idNegocio, final int idCliente) {
        return executor.ejecutar(new Callable<Carrito>() {
            @Override
            public Carrito call() {
                return consultar(idNegocio, idCliente);
            }
        });
    }

    public CompletableFuture<Carrito> agregarAsync(final long idNegocio, final int idCliente,
                                                   final int idProducto, final int cantidad) {
        return executor.ejecutar(new Callable<Carrito>() {
            @Override
            public Carrito call() {
                return agregar(idNegocio, idCliente, idProducto, cantidad);
            }
        });
    }

    public CompletableFuture<Carrito> eliminarItemAsync(final long idNegocio, final int idCliente,
                                                        final int idProducto) {
        return executor.ejecutar(new Callable<Carrito>() {
            @Override
            public Carrito call() {
                return eliminarItem(idNegocio, idCliente, idProducto);
            }
        });
    }

    public CompletableFuture<Void> vaciarAsync(final long idNegocio, final int idCliente) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                vaciar(idNegocio, idCliente);
            }
        });
    }

    private Carrito consultar(long idNegocio, int idCliente) {
        validarIdentidad(idNegocio, idCliente);
        Optional<CarritoPersistido> persistido = carritoRepository.obtener(idNegocio, idCliente);
        if (persistido.isEmpty()) {
            return new Carrito(idCliente);
        }
        return construir(idNegocio, persistido.get());
    }

    private Carrito agregar(long idNegocio, int idCliente, int idProducto, int cantidad) {
        validarIdentidad(idNegocio, idCliente);
        if (idProducto <= 0 || cantidad <= 0) {
            throw new IllegalArgumentException("El producto y la cantidad deben ser positivos.");
        }

        Producto producto = productoRepository.buscarPorId(idNegocio, idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el producto solicitado en este negocio."));
        if (!producto.puedeVenderse()) {
            throw new RecursoNoEncontradoException(
                    "El producto solicitado ya no está disponible.");
        }

        CarritoPersistido persistido = carritoRepository.agregarItem(
                idNegocio, idCliente, idProducto, cantidad, producto.getStockDisponible());
        return construir(idNegocio, persistido);
    }

    private Carrito eliminarItem(long idNegocio, int idCliente, int idProducto) {
        validarIdentidad(idNegocio, idCliente);
        if (idProducto <= 0) {
            throw new IllegalArgumentException("El producto debe ser positivo.");
        }
        CarritoPersistido persistido = carritoRepository.eliminarItem(
                idNegocio, idCliente, idProducto);
        return construir(idNegocio, persistido);
    }

    private void vaciar(long idNegocio, int idCliente) {
        validarIdentidad(idNegocio, idCliente);
        carritoRepository.eliminar(idNegocio, idCliente);
    }

    private Carrito construir(long idNegocio, CarritoPersistido persistido) {
        Carrito carrito = new Carrito(persistido.idCliente());
        List<Integer> ids = new ArrayList<>(persistido.cantidades().keySet());
        Collections.sort(ids);
        for (Integer idProducto : ids) {
            Optional<Producto> producto = productoRepository.buscarPorId(idNegocio, idProducto);
            if (producto.isEmpty() || !producto.get().puedeVenderse()) {
                continue;
            }
            carrito.agregarItem(new ItemCarrito(producto.get(), persistido.cantidades().get(idProducto)));
        }
        return carrito;
    }

    private void validarIdentidad(long idNegocio, int idCliente) {
        if (idNegocio <= 0 || idCliente <= 0) {
            throw new IllegalArgumentException("El negocio y el cliente deben ser positivos.");
        }
        if (negocioRepository == null) {
            return;
        }
        Optional<Negocio> negocio = negocioRepository.buscarPorId(idNegocio);
        if (negocio.isEmpty() || !negocio.get().estaOperativo()) {
            throw new RecursoNoEncontradoException("El negocio no está disponible.");
        }
    }
}
