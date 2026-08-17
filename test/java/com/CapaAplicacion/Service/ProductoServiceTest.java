package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.ProductosEventos;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private IProductoRepository repository;

    @Mock
    private EventBus eventBus;

    @InjectMocks
    private ProductoService service;

    private ProductoRequestDTO dtoValido() {
        return new ProductoRequestDTO("Mouse", new BigDecimal("50"), 15, CategoriaProducto.ELECTRONICA);
    }

    private Producto productoEnRepo(int id) {
        return new Producto("Mouse", new BigDecimal("50"), 15, 0, id, CategoriaProducto.ELECTRONICA);
    }

    @Test
    @DisplayName("guardarAsync: persiste el producto y publica evento")
    void guardar() throws Exception {
        service.guardarAsync(dtoValido()).get();

        ArgumentCaptor<Producto> productoCaptor = ArgumentCaptor.forClass(Producto.class);
        verify(repository).guardar(productoCaptor.capture());
        assertEquals("Mouse", productoCaptor.getValue().getNombre());

        ArgumentCaptor<ProductosEventos> eventoCaptor = ArgumentCaptor.forClass(ProductosEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertNotNull(eventoCaptor.getValue());
    }

    @Test
    @DisplayName("guardarAsync: rechaza precio inválido")
    void guardarPrecioInvalido() {
        ProductoRequestDTO dto = new ProductoRequestDTO("Mouse", BigDecimal.ZERO, 15, CategoriaProducto.ELECTRONICA);
        assertThrows(ExecutionException.class, () -> service.guardarAsync(dto).get());
        verify(repository, never()).guardar(any());
        verify(eventBus, never()).publicar(any());
    }

    @Test
    @DisplayName("buscarPorIdAsync: devuelve DTO si existe")
    void buscarPorId() throws Exception {
        when(repository.buscarPorId(1)).thenReturn(Optional.of(productoEnRepo(1)));
        Optional<ProductoResponseDTO> resultado = service.buscarPorIdAsync(1).get();
        assertTrue(resultado.isPresent());
        assertEquals("Mouse", resultado.get().nombre());
    }

    @Test
    @DisplayName("buscarPorIdAsync: vacío si no existe")
    void buscarPorIdInexistente() throws Exception {
        when(repository.buscarPorId(99)).thenReturn(Optional.empty());
        assertTrue(service.buscarPorIdAsync(99).get().isEmpty());
    }

    @Test
    @DisplayName("listarTodosAsync: mapea todos los productos")
    void listarTodos() throws Exception {
        when(repository.listarTodos()).thenReturn(List.of(productoEnRepo(1), productoEnRepo(2)));
        List<ProductoResponseDTO> resultado = service.listarTodosAsync().get();
        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("actualizarAsync: actualiza nombre, precio y stock")
    void actualizar() throws Exception {
        Producto existente = productoEnRepo(1);
        when(repository.buscarPorId(1)).thenReturn(Optional.of(existente));

        service.actualizarAsync(1, new ProductoRequestDTO("Teclado", new BigDecimal("80"), 30, CategoriaProducto.ELECTRONICA)).get();

        assertEquals("Teclado", existente.getNombre());
        assertEquals(new BigDecimal("80"), existente.getPrecio());
        assertEquals(30, existente.getStockFisico());
        verify(repository).actualizar(eq(1), eq(existente));
    }

    @Test
    @DisplayName("actualizarAsync: lanza error si el producto no existe")
    void actualizarInexistente() {
        when(repository.buscarPorId(99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class,
                () -> service.actualizarAsync(99, dtoValido()).get());
    }

    @Test
    @DisplayName("eliminarAsync: delega en el repositorio")
    void eliminar() throws Exception {
        service.eliminarAsync(2).get();
        verify(repository).eliminar(2);
    }
}