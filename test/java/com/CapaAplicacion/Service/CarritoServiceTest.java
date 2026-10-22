package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.CarritoPersistido;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.StockInsuficienteException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final long NEGOCIO = 1L;
    private static final int CLIENTE = 7;

    @Mock
    private ICarritoRepository carritoRepository;

    @Mock
    private IProductoRepository productoRepository;

    @Mock
    private INegocioRepository negocioRepository;

    private CarritoService service;

    @BeforeEach
    void setUp() {
        when(negocioRepository.buscarPorId(NEGOCIO))
                .thenReturn(Optional.of(new Negocio(
                        NEGOCIO, "Negocio", "negocio", "negocio@mail.com", true, null, null)));
        service = new CarritoService(carritoRepository, productoRepository, negocioRepository);
    }

    @Test
    @DisplayName("consultarAsync: devuelve un carrito vacío si no hay datos persistidos")
    void consultarVacio() throws Exception {
        when(carritoRepository.obtener(NEGOCIO, CLIENTE)).thenReturn(Optional.empty());

        Carrito carrito = service.consultarAsync(NEGOCIO, CLIENTE).get(10, TimeUnit.SECONDS);

        assertEquals(CLIENTE, carrito.getIdCliente());
        assertTrue(carrito.getItems().isEmpty());
        verify(carritoRepository).obtener(NEGOCIO, CLIENTE);
    }

    @Test
    @DisplayName("agregarAsync: persiste con el negocio autenticado y devuelve datos actuales")
    void agregarItem() throws Exception {
        Producto producto = producto(2, "Yerba", "100", 5);
        when(productoRepository.buscarPorId(NEGOCIO, 2)).thenReturn(Optional.of(producto));
        when(carritoRepository.agregarItem(NEGOCIO, CLIENTE, 2, 2, 5))
                .thenReturn(new CarritoPersistido(CLIENTE, Map.of(2, 2)));

        Carrito carrito = service.agregarAsync(NEGOCIO, CLIENTE, 2, 2)
                .get(10, TimeUnit.SECONDS);

        assertEquals(1, carrito.getItems().size());
        ItemCarrito item = carrito.getItems().get(2);
        assertEquals(2, item.getCantidad());
        assertEquals("Yerba", item.getProducto().getNombre());
        verify(carritoRepository).agregarItem(NEGOCIO, CLIENTE, 2, 2, 5);
    }

    @Test
    @DisplayName("agregarAsync: rechaza productos fuera del negocio")
    void agregarProductoInexistente() {
        when(productoRepository.buscarPorId(NEGOCIO, 99)).thenReturn(Optional.empty());

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.agregarAsync(NEGOCIO, CLIENTE, 99, 1).get(10, TimeUnit.SECONDS));

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        verify(carritoRepository, never()).agregarItem(
                anyLong(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("agregarAsync: rechaza cantidades no positivas")
    void agregarCantidadInvalida() {
        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.agregarAsync(NEGOCIO, CLIENTE, 1, 0).get(10, TimeUnit.SECONDS));

        assertInstanceOf(IllegalArgumentException.class, excepcion.getCause());
        verify(productoRepository, never()).buscarPorId(NEGOCIO, 1);
    }

    @Test
    @DisplayName("agregarAsync: propaga el límite de stock del repositorio")
    void agregarSinStock() {
        Producto producto = producto(2, "Yerba", "100", 1);
        when(productoRepository.buscarPorId(NEGOCIO, 2)).thenReturn(Optional.of(producto));
        when(carritoRepository.agregarItem(NEGOCIO, CLIENTE, 2, 2, 1))
                .thenThrow(new StockInsuficienteException("Sin stock"));

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.agregarAsync(NEGOCIO, CLIENTE, 2, 2).get(10, TimeUnit.SECONDS));

        assertInstanceOf(StockInsuficienteException.class, excepcion.getCause());
    }

    @Test
    @DisplayName("eliminarItemAsync: devuelve el carrito actualizado")
    void eliminarItem() throws Exception {
        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        cantidades.put(1, 2);
        cantidades.put(3, 1);
        when(carritoRepository.eliminarItem(NEGOCIO, CLIENTE, 1))
                .thenReturn(new CarritoPersistido(CLIENTE, cantidades));
        when(productoRepository.buscarPorId(NEGOCIO, 1)).thenReturn(Optional.of(producto(1, "A", "10", 5)));
        when(productoRepository.buscarPorId(NEGOCIO, 3)).thenReturn(Optional.of(producto(3, "B", "20", 5)));

        Carrito carrito = service.eliminarItemAsync(NEGOCIO, CLIENTE, 1)
                .get(10, TimeUnit.SECONDS);

        assertEquals(2, carrito.getItems().size());
        verify(carritoRepository).eliminarItem(NEGOCIO, CLIENTE, 1);
    }

    @Test
    @DisplayName("vaciarAsync: elimina solo el carrito del negocio y cliente autenticados")
    void vaciarCarrito() throws Exception {
        service.vaciarAsync(NEGOCIO, CLIENTE).get(10, TimeUnit.SECONDS);

        verify(carritoRepository).eliminar(NEGOCIO, CLIENTE);
        verify(carritoRepository, never()).eliminar(eq(2L), anyInt());
    }

    @Test
    @DisplayName("agregarAsync: rechaza productos inactivos")
    void agregarProductoInactivo() {
        Producto producto = producto(2, "Yerba", "100", 5);
        producto.setActivo(false);
        when(productoRepository.buscarPorId(NEGOCIO, 2)).thenReturn(Optional.of(producto));

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.agregarAsync(NEGOCIO, CLIENTE, 2, 1).get(10, TimeUnit.SECONDS));

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        verify(carritoRepository, never()).agregarItem(
                anyLong(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("consultarAsync: rechaza un negocio inactivo")
    void consultarNegocioInactivo() {
        when(negocioRepository.buscarPorId(NEGOCIO)).thenReturn(Optional.of(new Negocio(
                NEGOCIO, "Negocio", "negocio", "negocio@mail.com", false, null, null)));

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.consultarAsync(NEGOCIO, CLIENTE).get(10, TimeUnit.SECONDS));

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        verify(carritoRepository, never()).obtener(NEGOCIO, CLIENTE);
    }

    @Test
    @DisplayName("consultarAsync: omite productos que ya no existen")
    void consultarOmiteProductosAusentes() throws Exception {
        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        cantidades.put(1, 2);
        cantidades.put(3, 1);
        when(carritoRepository.obtener(NEGOCIO, CLIENTE))
                .thenReturn(Optional.of(new CarritoPersistido(CLIENTE, cantidades)));
        when(productoRepository.buscarPorId(NEGOCIO, 1)).thenReturn(Optional.of(producto(1, "A", "10", 5)));
        when(productoRepository.buscarPorId(NEGOCIO, 3)).thenReturn(Optional.empty());

        Carrito carrito = service.consultarAsync(NEGOCIO, CLIENTE).get(10, TimeUnit.SECONDS);

        assertEquals(1, carrito.getItems().size());
        assertTrue(carrito.getItems().containsKey(1));
    }

    private Producto producto(int id, String nombre, String precio, int stock) {
        Producto producto = new Producto(
                nombre, new BigDecimal(precio), stock, 0, id, CategoriaProducto.ALMACEN);
        producto.setIdNegocio(NEGOCIO);
        return producto;
    }
}
