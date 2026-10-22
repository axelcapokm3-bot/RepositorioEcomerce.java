package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaDominio.Entidades.Cliente;
import java.time.LocalDate;
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
class ClienteServiceTest {
    private static final long NEGOCIO = 1L;

    @Mock
    private IClienteRepositorio repository;

    @InjectMocks
    private ClienteService service;

    private ClienteRequestDTO dtoValido() {
        return new ClienteRequestDTO(
                "34567890", "20345678909", "Juan", "Perez",
                "Av. Siempre 742", LocalDate.now().minusYears(30), "Springfield");
    }

    private Cliente clienteEnRepo(int id) {
        return new Cliente(id, "34567890", "Juan", "Perez",
                "Av. Siempre 742", LocalDate.now().minusYears(30), "Springfield");
    }

    @Test
    @DisplayName("guardarAsync: convierte DTO a entidad y la persiste")
    void guardar() throws Exception {
        service.guardarAsync(NEGOCIO, dtoValido()).get();

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).guardar(captor.capture());
        assertEquals("Juan", captor.getValue().getNombre());
        assertEquals("34567890", captor.getValue().getDni());
        assertEquals(NEGOCIO, captor.getValue().getIdNegocio());
    }

    @Test
    @DisplayName("buscarPorIdAsync: devuelve DTO si existe")
    void buscarPorId() throws Exception {
        when(repository.buscarPorId(NEGOCIO, 1)).thenReturn(Optional.of(clienteEnRepo(1)));
        Optional<ClienteResponseDTO> resultado = service.buscarPorIdAsync(NEGOCIO, 1).get();
        assertTrue(resultado.isPresent());
        assertEquals("Juan Perez", resultado.get().nombreCompleto());
    }

    @Test
    @DisplayName("buscarPorIdAsync: vacío si no existe")
    void buscarPorIdInexistente() throws Exception {
        when(repository.buscarPorId(NEGOCIO, 99)).thenReturn(Optional.empty());
        assertTrue(service.buscarPorIdAsync(NEGOCIO, 99).get().isEmpty());
    }

    @Test
    @DisplayName("listarTodosAsync: mapea todos los clientes")
    void listarTodos() throws Exception {
        when(repository.listarTodos(NEGOCIO)).thenReturn(List.of(clienteEnRepo(1), clienteEnRepo(2)));
        List<ClienteResponseDTO> resultado = service.listarTodosAsync(NEGOCIO).get();
        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("actualizarAsync: persiste el cliente actualizado")
    void actualizar() throws Exception {
        when(repository.buscarPorId(NEGOCIO, 1)).thenReturn(Optional.of(clienteEnRepo(1)));
        service.actualizarAsync(NEGOCIO, 1, dtoValido()).get();
        verify(repository).actualizar(eq(NEGOCIO), eq(1), any(Cliente.class));
    }

    @Test
    @DisplayName("actualizarAsync: lanza error si el cliente no existe")
    void actualizarInexistente() {
        when(repository.buscarPorId(NEGOCIO, 99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class,
                () -> service.actualizarAsync(NEGOCIO, 99, dtoValido()).get());
    }

    @Test
    @DisplayName("eliminarAsync: delega en el repositorio")
    void eliminar() throws Exception {
        when(repository.buscarPorId(NEGOCIO, 2)).thenReturn(Optional.of(clienteEnRepo(2)));
        service.eliminarAsync(NEGOCIO, 2).get();
        verify(repository).eliminar(NEGOCIO, 2);
    }

    @Test
    @DisplayName("eliminarAsync: no borra un cliente inexistente o de otro negocio")
    void eliminarInexistente() {
        when(repository.buscarPorId(NEGOCIO, 99)).thenReturn(Optional.empty());

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> service.eliminarAsync(NEGOCIO, 99).get());

        assertInstanceOf(com.CapaDominio.Excepciones.RecursoNoEncontradoException.class,
                error.getCause());
        verify(repository, never()).eliminar(anyLong(), anyInt());
    }
}