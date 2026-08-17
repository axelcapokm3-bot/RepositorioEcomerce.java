package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.DTOentrada.CuentaRequestDTO;
import com.CapaAplicacion.DTOsalida.CuentaResponseDTO;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Utilidades.OfuscadorDeContraseñas;
import com.CapaDominio.Entidades.Cuenta;
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
import org.mindrot.jbcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    private static final String PEPPER = "EcommerseStrictPepper2026";

    @Mock
    private ICuentaRepositorio repository;

    @InjectMocks
    private CuentaService service;

    private CuentaRequestDTO dtoValido() {
        return new CuentaRequestDTO("jperez", "jperez@gmail.com", "Password123", 10, "CLIENTE", new BigDecimal("500"));
    }

    private Cuenta cuentaEnRepo(int id) {
        return new Cuenta(id, "jperez", "jperez@gmail.com", "hash123", "CLIENTE", 10, new BigDecimal("500"));
    }

    @Test
    @DisplayName("guardarAsync: guarda cuenta con contraseña ofuscada y hasheada con BCrypt")
    void guardarHasheaContrasena() throws Exception {
        when(repository.buscarPorCorreo("jperez@gmail.com")).thenReturn(Optional.empty());

        service.guardarAsync(dtoValido()).get();

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(repository).guardar(captor.capture());
        Cuenta guardada = captor.getValue();

        String ofuscada = OfuscadorDeContraseñas.ofuscadorEstricto("Password123", PEPPER);
        assertTrue(BCrypt.checkpw(ofuscada, guardada.getContrasenia()),
                "La contraseña guardada debe ser el BCrypt de la contraseña ofuscada");
        assertNotEquals("Password123", guardada.getContrasenia());
    }

    @Test
    @DisplayName("guardarAsync: rechaza si el correo ya está registrado")
    void guardarRechazaCorreoDuplicado() {
        when(repository.buscarPorCorreo("jperez@gmail.com")).thenReturn(Optional.of(cuentaEnRepo(1)));

        assertThrows(ExecutionException.class,
                () -> service.guardarAsync(dtoValido()).get());
        verify(repository, never()).guardar(any());
    }

    @Test
    @DisplayName("listarTodosAsync: mapea todas las cuentas a DTOs")
    void listarTodos() throws Exception {
        when(repository.listarTodos()).thenReturn(List.of(
                cuentaEnRepo(1),
                new Cuenta(2, "mgomez", "mgomez@gmail.com", "hash123", "CLIENTE", 11, new BigDecimal("100"))));

        List<CuentaResponseDTO> resultado = service.listarTodosAsync().get();
        assertEquals(2, resultado.size());
        assertEquals("jperez", resultado.get(0).nombreCuenta());
    }

    @Test
    @DisplayName("buscarPorIdAsync: devuelve DTO si existe")
    void buscarPorId() throws Exception {
        when(repository.buscarPorId(1)).thenReturn(Optional.of(cuentaEnRepo(1)));
        Optional<CuentaResponseDTO> resultado = service.buscarPorIdAsync(1).get();
        assertTrue(resultado.isPresent());
        assertEquals("jperez@gmail.com", resultado.get().correoElectronico());
    }

    @Test
    @DisplayName("buscarPorIdAsync: vacío si no existe")
    void buscarPorIdInexistente() throws Exception {
        when(repository.buscarPorId(99)).thenReturn(Optional.empty());
        assertTrue(service.buscarPorIdAsync(99).get().isEmpty());
    }

    @Test
    @DisplayName("actualizarAsync: re-hashea la nueva contraseña")
    void actualizarConNuevaContrasena() throws Exception {
        Cuenta existente = cuentaEnRepo(1);
        when(repository.buscarPorId(1)).thenReturn(Optional.of(existente));
        when(repository.buscarPorCorreo("jperez@gmail.com")).thenReturn(Optional.of(existente));

        service.actualizarAsync(1, dtoValido()).get();

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(repository).actualizar(eq(1), captor.capture());
        String ofuscada = OfuscadorDeContraseñas.ofuscadorEstricto("Password123", PEPPER);
        assertTrue(BCrypt.checkpw(ofuscada, captor.getValue().getContrasenia()));
    }

    @Test
    @DisplayName("actualizarAsync: rechaza correo ocupado por otro usuario")
    void actualizarRechazaCorreoAjeno() {
        Cuenta existente = cuentaEnRepo(1);
        Cuenta otro = new Cuenta(2, "otro", "jperez@gmail.com", "hash123", "CLIENTE", 12, BigDecimal.ZERO);
        when(repository.buscarPorId(1)).thenReturn(Optional.of(existente));
        when(repository.buscarPorCorreo("jperez@gmail.com")).thenReturn(Optional.of(otro));

        assertThrows(ExecutionException.class,
                () -> service.actualizarAsync(1, dtoValido()).get());
    }

    @Test
    @DisplayName("actualizarAsync: lanza error si la cuenta no existe")
    void actualizarCuentaInexistente() {
        when(repository.buscarPorId(99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class,
                () -> service.actualizarAsync(99, dtoValido()).get());
    }

    @Test
    @DisplayName("eliminarAsync: delega en el repositorio")
    void eliminar() throws Exception {
        service.eliminarAsync(3).get();
        verify(repository).eliminar(3);
    }

    @Test
    @DisplayName("transferirAsync: mueve el saldo entre cuentas")
    void transferir() throws Exception {
        Cuenta origen = new Cuenta(1, "origen", "o@mail.com", "Password123", "CLIENTE", 10, new BigDecimal("1000"));
        Cuenta destino = new Cuenta(2, "destino", "d@mail.com", "Password123", "CLIENTE", 11, new BigDecimal("100"));
        when(repository.buscarPorId(1)).thenReturn(Optional.of(origen));
        when(repository.buscarPorId(2)).thenReturn(Optional.of(destino));

        service.transferirAsync(1, 2, 400.0).get();

        assertEquals(0, new BigDecimal("600").compareTo(origen.getSaldo()));
        assertEquals(0, new BigDecimal("500").compareTo(destino.getSaldo()));
        verify(repository).actualizar(1, origen);
        verify(repository).actualizar(2, destino);
    }

    @Test
    @DisplayName("transferirAsync: rechaza montos no positivos")
    void transferirMontoInvalido() {
        assertThrows(ExecutionException.class,
                () -> service.transferirAsync(1, 2, 0).get());
        assertThrows(ExecutionException.class,
                () -> service.transferirAsync(1, 2, -50).get());
    }

    @Test
    @DisplayName("transferirAsync: rechaza cuentas inexistentes o iguales")
    void transferirRechazos() {
        when(repository.buscarPorId(1)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class,
                () -> service.transferirAsync(1, 2, 100).get());

        when(repository.buscarPorId(1)).thenReturn(Optional.of(cuentaEnRepo(1)));
        assertThrows(ExecutionException.class,
                () -> service.transferirAsync(1, 1, 100).get());
    }
}
