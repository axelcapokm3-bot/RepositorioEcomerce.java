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
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
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

class VentaServiceConcurrenciaTest {

    private static final long NEGOCIO = 1L;

    @Test
    @DisplayName("dos ventas concurrentes del mismo producto no sobrevenden stock")
    void dosVentasConcurrentesNoSobrevenden() throws Exception {
        RepositorioProductoConcurrente repositorio = new RepositorioProductoConcurrente();
        repositorio.registrar(productoEnMemoria(1, 10));

        IVentaRepository ventaRepository = mock(IVentaRepository.class);
        VentaService service = new VentaService(
                new TransaccionConRollback(repositorio),
                ventaRepository,
                repositorio,
                null,
                mock(EventBus.class),
                null,
                null);

        CountDownLatch inicio = new CountDownLatch(2);
        Callable<String> intento = () -> {
            Carrito carrito = new Carrito(1);
            carrito.agregarItem(new ItemCarrito(productoEnMemoria(1, 10), 6));
            inicio.countDown();
            try {
                inicio.await(5, TimeUnit.SECONDS);
                service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get(10, TimeUnit.SECONDS);
                return "OK";
            } catch (ExecutionException excepcion) {
                return excepcion.getCause().getClass().getSimpleName();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> primero = pool.submit(intento);
            Future<String> segundo = pool.submit(intento);
            List<String> resultados = List.of(
                    primero.get(15, TimeUnit.SECONDS),
                    segundo.get(15, TimeUnit.SECONDS));

            assertEquals(1, resultados.stream().filter("OK"::equals).count(), resultados.toString());
            assertEquals(1, resultados.stream()
                    .filter("StockInsuficienteException"::equals).count(), resultados.toString());
            assertEquals(4, repositorio.stockEnBase(1));
            verify(ventaRepository, times(1)).guardar(any(Venta.class), any(Connection.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("una falla a mitad del carrito revierte el stock ya descontado")
    void fallaAMitadDelCarritoRevierteElStock() throws Exception {
        RepositorioProductoConcurrente repositorio = new RepositorioProductoConcurrente();
        repositorio.registrar(productoEnMemoria(1, 10));

        IVentaRepository ventaRepository = mock(IVentaRepository.class);
        VentaService service = new VentaService(
                new TransaccionConRollback(repositorio),
                ventaRepository,
                repositorio,
                null,
                mock(EventBus.class),
                null,
                null);

        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(productoEnMemoria(1, 10), 2));
        carrito.agregarItem(new ItemCarrito(productoEnMemoria(2, 5), 1));

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get());

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        assertEquals(10, repositorio.stockEnBase(1), "El stock descontado debe revertirse");
        assertEquals(2, carrito.getItems().size(), "El carrito no debe vaciarse si la venta falla");
        verify(ventaRepository, never()).guardar(any(Venta.class), any(Connection.class));
    }

    private static Producto productoEnMemoria(int id, int stock) {
        Producto producto = new Producto("Producto " + id, new BigDecimal("100"),
                stock, 0, id, CategoriaProducto.ELECTRONICA);
        producto.setIdNegocio(NEGOCIO);
        return producto;
    }

    private static final class TransaccionConRollback implements ITransaccion {
        private final RepositorioProductoConcurrente repositorio;
        private final Connection conexion = mock(Connection.class);

        TransaccionConRollback(RepositorioProductoConcurrente repositorio) {
            this.repositorio = repositorio;
        }

        @Override
        public void ejecutarTransaccion(OperacionTransaccional operacion) {
            repositorio.iniciarTransaccion();
            try {
                operacion.ejecutar(conexion);
                repositorio.confirmarTransaccion();
            } catch (Exception excepcion) {
                repositorio.revertirTransaccion();
                if (excepcion instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(excepcion);
            }
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
