package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.OperacionTransaccional;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VentaServiceCancelacionTest {

    private static final long NEGOCIO = 1L;

    @Test
    @DisplayName("dos cancelaciones concurrentes reintegran el stock una sola vez")
    void dosCancelacionesConcurrentesReintegranUnaVez() throws Exception {
        RepositorioProductoConcurrente productos = new RepositorioProductoConcurrente();
        productos.registrar(productoEnMemoria(1, 4));
        RepositorioVentaConcurrente ventas = new RepositorioVentaConcurrente();
        ventas.registrar(ventaPendienteConDetalle(7, 1, 2));
        EventBus eventBus = mock(EventBus.class);

        VentaService service = new VentaService(
                new TransaccionConRollback(ventas, productos),
                ventas,
                productos,
                null,
                eventBus,
                null,
                null);

        CountDownLatch inicio = new CountDownLatch(2);
        Callable<Void> intento = () -> {
            inicio.countDown();
            inicio.await(5, TimeUnit.SECONDS);
            service.cancelarVentaAsync(NEGOCIO, 7).get(10, TimeUnit.SECONDS);
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> primero = pool.submit(intento);
            Future<Void> segundo = pool.submit(intento);
            primero.get(15, TimeUnit.SECONDS);
            segundo.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(6, productos.stockEnBase(1));
        assertEquals(EstadoVenta.CANCELADO, ventas.estadoEnBase(7));
        verify(eventBus, times(1)).publicar(any());
    }

    @Test
    @DisplayName("un fallo a mitad del reintegro revierte stock y venta")
    void falloAMitadRevierteTodo() {
        RepositorioProductoConcurrente productos = new RepositorioProductoConcurrente();
        productos.registrar(productoEnMemoria(1, 4));
        RepositorioVentaConcurrente ventas = new RepositorioVentaConcurrente();
        Venta venta = ventaPendienteConDetalle(7, 1, 2);
        venta.getDetalles().add(detalle(2, 1));
        ventas.registrar(venta);
        EventBus eventBus = mock(EventBus.class);

        VentaService service = new VentaService(
                new TransaccionConRollback(ventas, productos),
                ventas,
                productos,
                null,
                eventBus,
                null,
                null);

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.cancelarVentaAsync(NEGOCIO, 7).get());

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        assertEquals(4, productos.stockEnBase(1), "El stock del primer producto debe revertirse");
        assertEquals(EstadoVenta.PENDIENTE, ventas.estadoEnBase(7));
        verify(eventBus, never()).publicar(any());
    }

    private static Venta ventaPendienteConDetalle(int idVenta, int idProducto, int cantidad) {
        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setIdNegocio(NEGOCIO);
        venta.setId(idVenta);
        venta.getDetalles().add(detalle(idProducto, cantidad));
        return venta;
    }

    private static DetalleVenta detalle(int idProducto, int cantidad) {
        DetalleVenta detalle = new DetalleVenta(idProducto, new BigDecimal("100"), cantidad);
        detalle.setIdNegocio(NEGOCIO);
        return detalle;
    }

    private static Producto productoEnMemoria(int id, int stock) {
        Producto producto = new Producto("Producto " + id, new BigDecimal("100"),
                stock, 0, id, CategoriaProducto.ELECTRONICA);
        producto.setIdNegocio(NEGOCIO);
        return producto;
    }

    private static final class TransaccionConRollback implements ITransaccion {
        private final RepositorioVentaConcurrente ventas;
        private final RepositorioProductoConcurrente productos;
        private final Connection conexion = mock(Connection.class);

        TransaccionConRollback(RepositorioVentaConcurrente ventas,
                               RepositorioProductoConcurrente productos) {
            this.ventas = ventas;
            this.productos = productos;
        }

        @Override
        public void ejecutarTransaccion(OperacionTransaccional operacion) {
            ventas.iniciarTransaccion();
            productos.iniciarTransaccion();
            try {
                operacion.ejecutar(conexion);
                ventas.confirmarTransaccion();
                productos.confirmarTransaccion();
            } catch (Exception excepcion) {
                ventas.revertirTransaccion();
                productos.revertirTransaccion();
                if (excepcion instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(excepcion);
            }
        }
    }

    private static final class RepositorioVentaConcurrente implements IVentaRepository {
        private final Map<Integer, Venta> filas = new ConcurrentHashMap<>();
        private final Map<Integer, ReentrantLock> bloqueos = new ConcurrentHashMap<>();
        private final ThreadLocal<List<Venta>> cambiosSinConfirmar =
                ThreadLocal.withInitial(ArrayList::new);

        void registrar(Venta venta) {
            filas.put(venta.getId(), copiaDe(venta));
        }

        EstadoVenta estadoEnBase(int id) {
            Venta venta = filas.get(id);
            return venta == null ? null : venta.getEstadoCompra();
        }

        void iniciarTransaccion() {
            cambiosSinConfirmar.set(new ArrayList<>());
        }

        void confirmarTransaccion() {
            cambiosSinConfirmar.remove();
        }

        void revertirTransaccion() {
            List<Venta> cambios = cambiosSinConfirmar.get();
            for (int i = cambios.size() - 1; i >= 0; i--) {
                Venta anterior = cambios.get(i);
                filas.put(anterior.getId(), anterior);
            }
            cambiosSinConfirmar.remove();
        }

        private ReentrantLock bloqueo(int id) {
            return bloqueos.computeIfAbsent(id, clave -> new ReentrantLock());
        }

        @Override
        public void guardar(Venta venta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Venta> buscarPorId(long idNegocio, int id) {
            Venta venta = filas.get(id);
            if (venta == null || venta.getIdNegocio() != idNegocio) {
                return Optional.empty();
            }
            return Optional.of(copiaDe(venta));
        }

        @Override
        public Optional<Venta> buscarPorIdParaUpdate(long idNegocio, int id, Connection connection) {
            ReentrantLock bloqueo = bloqueo(id);
            bloqueo.lock();
            Venta venta = filas.get(id);
            if (venta == null || venta.getIdNegocio() != idNegocio) {
                bloqueo.unlock();
                return Optional.empty();
            }
            return Optional.of(copiaDe(venta));
        }

        @Override
        public List<Venta> listarTodas(long idNegocio) {
            return List.of();
        }

        @Override
        public void actualizar(long idNegocio, int id, Venta venta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long idNegocio, int id, Venta venta, Connection connection) {
            cambiosSinConfirmar.get().add(copiaDe(filas.get(id)));
            filas.put(id, copiaDe(venta));
            bloqueo(id).unlock();
        }

        private static Venta copiaDe(Venta origen) {
            Venta copia = new Venta(
                    origen.getIdCliente(),
                    origen.getTipoPago(),
                    origen.getClaveIdempotenciaSolicitud());
            copia.setId(origen.getId());
            copia.setIdNegocio(origen.getIdNegocio());
            for (DetalleVenta detalle : origen.getDetalles()) {
                DetalleVenta copiaDetalle = new DetalleVenta(
                        detalle.getIdProducto(), detalle.getPrecioUnitario(), detalle.getCantidad());
                copiaDetalle.setIdNegocio(origen.getIdNegocio());
                copia.getDetalles().add(copiaDetalle);
            }
            copia.rehidratarEstado(origen.getEstadoCompra());
            return copia;
        }
    }

    private static final class RepositorioProductoConcurrente implements IProductoRepository {
        private final Map<Integer, Producto> filas = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> stockEnBase = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> reservadoEnBase = new ConcurrentHashMap<>();
        private final Map<Integer, ReentrantLock> bloqueos = new ConcurrentHashMap<>();
        private final ThreadLocal<List<int[]>> cambiosSinConfirmar =
                ThreadLocal.withInitial(ArrayList::new);

        void registrar(Producto producto) {
            filas.put(producto.getId(), producto);
            stockEnBase.put(producto.getId(), producto.getStockFisico());
            reservadoEnBase.put(producto.getId(), producto.getStockReservado());
        }

        int stockEnBase(int id) {
            return stockEnBase.getOrDefault(id, 0);
        }

        void iniciarTransaccion() {
            cambiosSinConfirmar.set(new ArrayList<>());
        }

        void confirmarTransaccion() {
            cambiosSinConfirmar.remove();
        }

        void revertirTransaccion() {
            List<int[]> cambios = cambiosSinConfirmar.get();
            for (int i = cambios.size() - 1; i >= 0; i--) {
                int[] cambio = cambios.get(i);
                stockEnBase.put(cambio[0], cambio[1]);
                reservadoEnBase.put(cambio[0], cambio[2]);
            }
            cambiosSinConfirmar.remove();
        }

        private ReentrantLock bloqueo(int id) {
            return bloqueos.computeIfAbsent(id, clave -> new ReentrantLock());
        }

        @Override
        public void guardar(Producto producto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Producto> buscarPorId(long idNegocio, int id) {
            Producto producto = filas.get(id);
            if (producto == null || producto.getIdNegocio() != idNegocio) {
                return Optional.empty();
            }
            return Optional.of(copiaFresca(producto));
        }

        @Override
        public List<Producto> listarTodos(long idNegocio) {
            return List.of();
        }

        @Override
        public Optional<Producto> buscarPorIdParaUpdate(long idNegocio, int id, Connection connection) {
            ReentrantLock bloqueo = bloqueo(id);
            bloqueo.lock();
            Producto producto = filas.get(id);
            if (producto == null || producto.getIdNegocio() != idNegocio) {
                bloqueo.unlock();
                return Optional.empty();
            }
            return Optional.of(copiaFresca(producto));
        }

        @Override
        public void actualizar(long idNegocio, int id, Producto producto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long idNegocio, int id, Producto producto, Connection connection) {
            cambiosSinConfirmar.get().add(new int[]{
                    id,
                    stockEnBase.getOrDefault(id, 0),
                    reservadoEnBase.getOrDefault(id, 0)});
            stockEnBase.put(id, producto.getStockFisico());
            reservadoEnBase.put(id, producto.getStockReservado());
            bloqueo(id).unlock();
        }

        @Override
        public void eliminar(long idNegocio, int id) {
            throw new UnsupportedOperationException();
        }

        private Producto copiaFresca(Producto producto) {
            return new Producto(
                    producto.getNombre(),
                    producto.getPrecio(),
                    stockEnBase.getOrDefault(producto.getId(), 0),
                    reservadoEnBase.getOrDefault(producto.getId(), 0),
                    producto.getId(),
                    producto.getCategoria());
        }
    }
}
