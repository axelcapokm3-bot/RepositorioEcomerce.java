package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaInfraestructura.Implementacion.RepositorioCliente;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteServiceAislamientoTest {

    private static final long TENANT_A = 901L;
    private static final long TENANT_B = 902L;

    private RepositorioCliente repositorio;
    private ClienteService servicio;
    private Cliente clienteA;
    private Cliente clienteB;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioCliente();
        servicio = new ClienteService(repositorio);
        clienteA = crearCliente("30111111", "Ana", "Alvarez", TENANT_A);
        clienteB = crearCliente("30222222", "Beto", "Benitez", TENANT_B);
        repositorio.guardar(clienteA);
        repositorio.guardar(clienteB);
    }

    @AfterAll
    static void cerrarExecutors() {
        RegistroExecutors.cerrarTodos();
    }

    @Test
    @DisplayName("listar solo devuelve clientes del negocio autenticado")
    void listarSoloDelNegocioAutenticado() throws Exception {
        List<ClienteResponseDTO> lista = servicio.listarTodosAsync(TENANT_A)
                .get(5, TimeUnit.SECONDS);

        assertEquals(1, lista.size());
        assertEquals(clienteA.getId(), lista.get(0).id());
    }

    @Test
    @DisplayName("buscar un cliente de otro negocio devuelve vacio")
    void buscarClienteDeOtroNegocioDevuelveVacio() throws Exception {
        Optional<ClienteResponseDTO> encontrado = servicio.buscarPorIdAsync(TENANT_A, clienteB.getId())
                .get(5, TimeUnit.SECONDS);

        assertTrue(encontrado.isEmpty());
    }

    @Test
    @DisplayName("actualizar un cliente de otro negocio responde 404 y no modifica datos")
    void actualizarClienteDeOtroNegocioNoModifica() throws Exception {
        ClienteRequestDTO cambios = datos("30222222", "Intruso", "Intruso");

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> servicio.actualizarAsync(TENANT_A, clienteB.getId(), cambios)
                        .get(5, TimeUnit.SECONDS));

        assertInstanceOf(RecursoNoEncontradoException.class, error.getCause());
        assertEquals("Beto", repositorio.buscarPorId(TENANT_B, clienteB.getId())
                .orElseThrow().getNombre());
    }

    @Test
    @DisplayName("eliminar un cliente de otro negocio responde 404 y no lo da de baja")
    void eliminarClienteDeOtroNegocioNoLoDaDeBaja() throws Exception {
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> servicio.eliminarAsync(TENANT_A, clienteB.getId())
                        .get(5, TimeUnit.SECONDS));

        assertInstanceOf(RecursoNoEncontradoException.class, error.getCause());
        assertTrue(repositorio.buscarPorId(TENANT_B, clienteB.getId()).orElseThrow().isActivo());
    }

    @Test
    @DisplayName("guardar asigna el negocio autenticado y no el enviado por el cliente")
    void guardarAsignaNegocioAutenticado() throws Exception {
        ClienteRequestDTO nuevo = datos("30333333", "Carla", "Costa");

        servicio.guardarAsync(TENANT_A, nuevo).get(5, TimeUnit.SECONDS);

        Cliente guardado = repositorio.buscarPorDni(TENANT_A, "30333333").orElseThrow();
        assertEquals(TENANT_A, guardado.getIdNegocio());
        assertTrue(repositorio.buscarPorDni(TENANT_B, "30333333").isEmpty());
    }

    private static Cliente crearCliente(String dni, String nombre, String apellido, long idNegocio) {
        Cliente cliente = new Cliente(0, dni, nombre, apellido, "Calle 1",
                LocalDate.of(1990, 1, 1), "CABA");
        cliente.setIdNegocio(idNegocio);
        return cliente;
    }

    private static ClienteRequestDTO datos(String dni, String nombre, String apellido) {
        return new ClienteRequestDTO(dni, null, nombre, apellido, "Calle 2",
                LocalDate.of(1990, 1, 1), "CABA");
    }
}
