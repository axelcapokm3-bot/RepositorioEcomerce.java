package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.DTOentrada.CuentaIngresoDTO;
import com.CapaAplicacion.DTOentrada.CuentaRegistroDTO;
import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaNegocioRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.OperacionTransaccional;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaDominio.Entidades.EstadoCuenta;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaDominio.Entidades.VinculoCuentaNegocio;
import com.CapaInfraestructura.Implementacion.AdaptadorContrasena;
import com.CapaInfraestructura.Implementacion.ProveedorTokenAcceso;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionServiceTest {
    private CuentaRepositorioFalso cuentas;
    private VinculoRepositorioFalso vinculos;
    private NegocioRepositorioFalso negocios;
    private ClienteRepositorioFalso clientes;
    private AutenticacionService servicio;

    @BeforeEach
    void preparar() {
        cuentas = new CuentaRepositorioFalso();
        vinculos = new VinculoRepositorioFalso();
        negocios = new NegocioRepositorioFalso();
        clientes = new ClienteRepositorioFalso();
        Cliente cliente = new Cliente(5, "30123456", "Juan", "Perez", "Calle 123",
                LocalDate.of(1990, 5, 15), "Springfield");
        cliente.setIdNegocio(1L);
        clientes.agregar(cliente);
        Cliente otro = new Cliente(9, "30987654", "Ana", "Gomez", "Calle 456",
                LocalDate.of(1992, 3, 10), "Shelbyville");
        otro.setIdNegocio(2L);
        clientes.agregar(otro);
        Cliente libre = new Cliente(12, "30555555", "Carlos", "Diaz", "Calle 789",
                LocalDate.of(1988, 1, 1), "Capital");
        libre.setIdNegocio(1L);
        clientes.agregar(libre);
        Cliente libreDos = new Cliente(13, "30666666", "Lucia", "Sosa", "Calle 321",
                LocalDate.of(1995, 6, 20), "Rosario");
        libreDos.setIdNegocio(1L);
        clientes.agregar(libreDos);
        servicio = new AutenticacionService(cuentas, clientes, vinculos, negocios, new AdaptadorContrasena(),
                new ProveedorTokenAcceso(), new TransaccionFalsa(cuentas, clientes, vinculos),
                new EjecutorDirecto(), "token-setup-prueba");
    }

    private Principal principalDe(SesionResponseDTO sesion) {
        return new Principal(sesion.idCuenta(), sesion.idCliente(), sesion.idNegocio(),
                RolUsuario.desdeTexto(sesion.rol()));
    }

    private static CuentaRegistroDTO dtoVinculacion(String nombreCuenta, String correo,
                                                    String contrasenia, String dni, String rol) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, dni, rol,
                null, null, null, null, null);
    }

    private static CuentaRegistroDTO dtoConCliente(String nombreCuenta, String correo, String contrasenia,
                                                   String dni, String nombreCliente, String apellidoCliente,
                                                   LocalDate fechaNacimiento, String direccion, String localidad) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, dni, "CLIENTE",
                nombreCliente, apellidoCliente, fechaNacimiento, direccion, localidad);
    }

    private static CuentaRegistroDTO dtoPerfilJuan(String nombreCuenta, String correo,
                                                    String contrasenia, String rol) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, "30123456", rol,
                "Juan", "Perez", LocalDate.of(1990, 5, 15), "Calle 123", "Springfield");
    }

    private static CuentaRegistroDTO dtoPerfilAna(String nombreCuenta, String correo,
                                                   String contrasenia, String rol) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, "30987654", rol,
                "Ana", "Gomez", LocalDate.of(1992, 3, 10), "Calle 456", "Shelbyville");
    }

    private static CuentaRegistroDTO dtoPerfilCarlos(String nombreCuenta, String correo,
                                                      String contrasenia, String rol) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, "30555555", rol,
                "Carlos", "Diaz", LocalDate.of(1988, 1, 1), "Calle 789", "Capital");
    }

    private static CuentaRegistroDTO dtoPerfilLucia(String nombreCuenta, String correo,
                                                     String contrasenia, String rol) {
        return new CuentaRegistroDTO(nombreCuenta, correo, contrasenia, "30666666", rol,
                "Lucia", "Sosa", LocalDate.of(1995, 6, 20), "Calle 321", "Rosario");
    }

    @Test
    void registraClienteYDevuelveSesion() throws Exception {
        CuentaRegistroDTO dto = dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null);
        SesionResponseDTO sesion = servicio.registrarAsync(1L, dto, null).get();
        assertNotNull(sesion.token());
        assertEquals("CLIENTE", sesion.rol());
        assertEquals(5, sesion.idCliente());
        assertEquals(1L, sesion.idNegocio());
        assertTrue(sesion.expiraEn() > 0);
    }

    @Test
    void rechazaDniNuevoSinDatosDeClienteYSinPersistir() {
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L,
                        dtoVinculacion("jperez", "jperez@mail.com", "Segura123", "12345678", null), null).get();
            }
        });
        assertEquals(0, cuentas.contarCuentas());
        assertTrue(clientes.buscarPorDni(1L, "12345678").isEmpty());
    }

    @Test
    void creaClienteNuevoYVinculaSesion() throws Exception {
        SesionResponseDTO sesion = servicio.registrarAsync(1L,
                dtoConCliente("nuevo", "nuevo@mail.com", "Segura123", "12345678",
                        "Pedro", "Ramirez", LocalDate.of(1985, 7, 20), "Calle Falsa 123", "Springfield"),
                null).get();
        assertEquals("CLIENTE", sesion.rol());
        assertEquals(1L, sesion.idNegocio());
        assertTrue(sesion.idCliente() > 0);
        Optional<Cliente> creado = clientes.buscarPorDni(1L, "12345678");
        assertTrue(creado.isPresent());
        assertEquals(sesion.idCliente(), creado.get().getId());
        assertEquals("Pedro", creado.get().getNombre());
        assertEquals("Ramirez", creado.get().getApellido());
        assertEquals("Springfield", creado.get().getLocalidad());
        assertEquals(1L, creado.get().getIdNegocio());
        assertEquals(1, cuentas.contarCuentas());
    }

    @Test
    void creaClienteNuevoAunqueElDniExistaEnOtroNegocio() throws Exception {
        SesionResponseDTO sesion = servicio.registrarAsync(1L,
                dtoConCliente("jperez", "jperez@mail.com", "Segura123", "30987654",
                        "Ana", "Gomez", LocalDate.of(1992, 3, 10), "Calle 456", "Shelbyville"),
                null).get();
        assertEquals(1L, sesion.idNegocio());
        Optional<Cliente> creado = clientes.buscarPorDni(1L, "30987654");
        assertTrue(creado.isPresent());
        assertEquals(sesion.idCliente(), creado.get().getId());
        assertTrue(clientes.buscarPorId(2L, 9).isPresent());
    }

    @Test
    void rechazaPerfilLibreConIdentidadQueNoCoincideSinPersistir() {
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L,
                        dtoConCliente("jperez", "jperez@mail.com", "Segura123", "30123456",
                                "Pedro", "Ramirez", LocalDate.of(1970, 1, 1), "Otra Calle 999",
                                "Otro Pueblo"),
                        null).get();
            }
        });
        assertEquals(0, cuentas.contarCuentas());
        Cliente existente = clientes.buscarPorId(1L, 5).get();
        assertEquals("Juan", existente.getNombre());
        assertEquals("Perez", existente.getApellido());
        assertEquals("Calle 123", existente.getDireccion());
        assertEquals("Springfield", existente.getLocalidad());
        assertTrue(vinculos.listarPorCuenta(1L).isEmpty());
    }

    @Test
    void rechazaDatosDeClienteInvalidosSinPersistir() {
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L,
                        dtoConCliente("jperez", "jperez@mail.com", "Segura123", "12345678",
                                "Pedro", "Ramirez", LocalDate.now().minusYears(17), "Calle 1", "Springfield"),
                        null).get();
            }
        });
        assertEquals(0, cuentas.contarCuentas());
        assertTrue(clientes.buscarPorDni(1L, "12345678").isEmpty());
    }

    @Test
    void rechazaCorreoDuplicadoSinCrearClienteNuevo() throws Exception {
        servicio.registrarAsync(1L, dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(2L,
                        dtoConCliente("jperez", "jperez@mail.com", "Otra9999", "12345678",
                                "Pedro", "Ramirez", LocalDate.of(1985, 7, 20), "Calle 1", "Springfield"),
                        null).get();
            }
        });
        assertEquals(1, cuentas.contarCuentas());
        assertTrue(clientes.buscarPorDni(2L, "12345678").isEmpty());
    }

    @Test
    void rechazaVinculoDuplicadoDeAdminSinCrearClienteNuevo() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal admin = new Principal(primera.idCuenta(), 5, 1L, RolUsuario.ADMIN);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L,
                        new CuentaRegistroDTO("jperez", "jperez@mail.com", "Segura123", "12345678", "ADMIN",
                                "Pedro", "Ramirez", LocalDate.of(1985, 7, 20), "Calle 1", "Springfield"),
                        admin).get();
            }
        });
        assertTrue(clientes.buscarPorDni(1L, "12345678").isEmpty());
    }

    @Test
    void rechazaCorreoDuplicado() throws Exception {
        CuentaRegistroDTO dto = dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null);
        servicio.registrarAsync(1L, dto, null).get();
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L, dto, null).get();
            }
        });
    }

    @Test
    void ingresaConCredencialesValidas() throws Exception {
        servicio.registrarAsync(1L, dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        SesionResponseDTO sesion = servicio.ingresarAsync(1L, new CuentaIngresoDTO("jperez@mail.com", "Segura123")).get();
        assertNotNull(sesion.token());
        assertEquals(1L, sesion.idNegocio());
    }

    @Test
    void rechazaCredencialesInvalidas() {
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.ingresarAsync(1L, new CuentaIngresoDTO("nadie@mail.com", "Segura123")).get();
            }
        });
    }

    @Test
    void rechazaIngresoEnNegocioSinVinculo() throws Exception {
        servicio.registrarAsync(1L, dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.ingresarAsync(2L, new CuentaIngresoDTO("jperez@mail.com", "Segura123")).get();
            }
        });
    }

    @Test
    void vinculaSegundoNegocioConContrasenia() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);

        SesionResponseDTO segunda =
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
        assertEquals("CLIENTE", segunda.rol());
        assertEquals(2L, segunda.idNegocio());
        assertEquals(9, segunda.idCliente());
        assertEquals(primera.idCuenta(), segunda.idCuenta());

        SesionResponseDTO ingreso =
                servicio.ingresarAsync(2L, new CuentaIngresoDTO("jperez@mail.com", "Segura123")).get();
        assertNotNull(ingreso.token());
    }

    @Test
    void rechazaVinculoConContraseniaIncorrectaODuplicado() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);

        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Otra9999").get();
            }
        });
        servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
            }
        });
    }

    @Test
    void cambiaNegocioActivoSoloConVinculo() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();

        SesionResponseDTO cambiada = servicio.cambiarNegocioAsync(solicitante, 2L).get();
        assertEquals(2L, cambiada.idNegocio());

        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.cambiarNegocioAsync(solicitante, 3L).get();
            }
        });
    }

    @Test
    void rechazaQueAdminVinculeCuentaExistenteDeOtroTitular() throws Exception {
        SesionResponseDTO victima = servicio.registrarAsync(2L,
                dtoPerfilAna("base", "base@mail.com", "Segura123", null), null).get();
        SesionResponseDTO bootstrap = servicio.registrarAsync(1L,
                dtoPerfilCarlos("jefe", "jefe@mail.com", "Segura123", "ADMIN"),
                null, "token-setup-prueba").get();
        assertEquals("ADMIN", bootstrap.rol());

        CuentaRegistroDTO ataque = dtoPerfilJuan("base", "base@mail.com", "Segura123", "ADMIN");
        Principal admin = principalDe(bootstrap);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L, ataque, admin).get();
            }
        });
        assertEquals(2, cuentas.contarCuentas());
        assertTrue(vinculos.buscarPorCuentaYNegocio(victima.idCuenta(), 1L).isEmpty());
    }

    @Test
    void cuentaBloqueadaNoIngresa() throws Exception {
        servicio.registrarAsync(1L, dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        cuentas.bloquear("jperez@mail.com");
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.ingresarAsync(1L, new CuentaIngresoDTO("jperez@mail.com", "Segura123")).get();
            }
        });
    }

    @Test
    void listaNegociosVinculadosDeLaCuenta() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();

        java.util.List<com.CapaAplicacion.DTOsalida.VinculoResponseDTO> negocios =
                servicio.listarNegociosAsync(solicitante).get();
        assertEquals(2, negocios.size());
        assertTrue(negocios.stream().anyMatch(v -> v.idNegocio() == 1L && v.rol().equals("CLIENTE")
                && "Tienda Uno".equals(v.nombreNegocio()) && "uno".equals(v.subdominio())));
        assertTrue(negocios.stream().anyMatch(v -> v.idNegocio() == 2L && v.idCliente() == 9
                && "Tienda Dos".equals(v.nombreNegocio())));
    }

    @Test
    void rechazaVinculoYCambioANegocioInactivo() throws Exception {
        negocios.desactivar(2L);
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
            }
        });
        vinculos.forzar(primera.idCuenta(), 2L, 9, RolUsuario.CLIENTE);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.cambiarNegocioAsync(solicitante, 2L).get();
            }
        });
    }

    @Test
    void vinculoBloqueadoImpideCambiarDeNegocio() throws Exception {        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
        vinculos.bloquear(primera.idCuenta(), 2L);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.cambiarNegocioAsync(solicitante, 2L).get();
            }
        });
    }

    @Test
    void rechazaVinculoConIdentidadDeOtroPerfilSinCrearVinculo() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Pedro", "Ramirez",
                        LocalDate.of(1970, 1, 1), "Segura123").get();
            }
        });
        assertTrue(vinculos.buscarPorCuentaYNegocio(primera.idCuenta(), 2L).isEmpty());
    }

    @Test
    void rechazaVincularPerfilYaVinculadoAOtraCuenta() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        SesionResponseDTO duenio = servicio.registrarAsync(2L,
                dtoPerfilAna("ana", "ana@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                        LocalDate.of(1992, 3, 10), "Segura123").get();
            }
        });
        assertEquals(duenio.idCuenta(),
                vinculos.buscarPorCuentaYNegocio(duenio.idCuenta(), 2L).get().getIdCuenta());
        assertTrue(vinculos.buscarPorCuentaYNegocio(primera.idCuenta(), 2L).isEmpty());
    }

    @Test
    void reutilizaVinculoInactivoAlVolverARegistrar() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        vinculos.bloquear(primera.idCuenta(), 1L);
        assertTrue(!vinculos.buscarPorCuentaYNegocio(primera.idCuenta(), 1L).get().estaActivo());

        SesionResponseDTO segunda = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        assertEquals(primera.idCuenta(), segunda.idCuenta());
        assertEquals(primera.idCliente(), segunda.idCliente());
        assertTrue(vinculos.buscarPorCuentaYNegocio(primera.idCuenta(), 1L).get().estaActivo());
        assertEquals(1, vinculos.listarPorCuenta(primera.idCuenta()).size());
        assertEquals(1, cuentas.contarCuentas());
    }

    @Test
    void registroConCorreoExistenteYContraseniaCorrectaVinculaOtroNegocio() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        SesionResponseDTO segunda = servicio.registrarAsync(2L,
                dtoPerfilAna("jperez", "jperez@mail.com", "Segura123", null), null).get();
        assertEquals(primera.idCuenta(), segunda.idCuenta());
        assertEquals(9, segunda.idCliente());
        assertEquals(1, cuentas.contarCuentas());
        assertEquals(2, vinculos.listarPorCuenta(primera.idCuenta()).size());
    }

    @Test
    void registrarRechazaNegocioInactivo() throws Exception {
        negocios.desactivar(2L);
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(2L,
                        dtoPerfilAna("ana", "ana@mail.com", "Segura123", null), null).get();
            }
        });
        assertEquals(0, cuentas.contarCuentas());
    }

    @Test
    void listaNegociosOmiteVinculosYNegociosInactivos() throws Exception {
        SesionResponseDTO primera = servicio.registrarAsync(1L,
                dtoPerfilJuan("jperez", "jperez@mail.com", "Segura123", null), null).get();
        Principal solicitante = principalDe(primera);
        servicio.vincularNegocioAsync(solicitante, 2L, "30987654", "Ana", "Gomez",
                LocalDate.of(1992, 3, 10), "Segura123").get();
        vinculos.bloquear(primera.idCuenta(), 2L);
        negocios.desactivar(2L);
        assertEquals(1, servicio.listarNegociosAsync(solicitante).get().size());

        negocios.desactivar(1L);
        assertTrue(servicio.listarNegociosAsync(solicitante).get().isEmpty());
    }

    @Test
    void soloAdminOCuentaConTokenSetupCreaAdmin() throws Exception {
        servicio.registrarAsync(1L, dtoPerfilJuan("base", "base@mail.com", "Segura123", null), null).get();
        CuentaRegistroDTO admin = dtoPerfilCarlos("jefe", "jefe@mail.com", "Segura123", "ADMIN");
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L, admin, null).get();
            }
        });
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                servicio.registrarAsync(1L, admin, null, "token-incorrecto").get();
            }
        });
        SesionResponseDTO bootstrap = servicio.registrarAsync(1L, admin, null, "token-setup-prueba").get();
        assertEquals("ADMIN", bootstrap.rol());
        assertEquals(12, bootstrap.idCliente());
        CuentaRegistroDTO otro = dtoPerfilLucia("jefe2", "jefe2@mail.com", "Segura123", "ADMIN");
        Principal solicitante = principalDe(bootstrap);
        SesionResponseDTO sesion = servicio.registrarAsync(1L, otro, solicitante).get();
        assertEquals("ADMIN", sesion.rol());
        assertEquals(13, sesion.idCliente());
    }

    private static final class CuentaRepositorioFalso implements ICuentaRepositorio {
        private final Map<String, Cuenta> porCorreo = new HashMap<>();
        private int secuencia = 1;

        @Override
        public void guardar(Cuenta cuenta) {
            if (cuenta.getId() == 0) {
                cuenta.setId(secuencia++);
            }
            porCorreo.put(cuenta.getCorreoElectronico(), cuenta);
        }

        @Override
        public void guardar(Connection conexion, Cuenta cuenta) {
            guardar(cuenta);
        }

        @Override
        public Optional<Cuenta> buscarPorId(long id) {
            for (Cuenta cuenta : porCorreo.values()) {
                if (cuenta.getId() == id) {
                    return Optional.of(cuenta);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Cuenta> buscarPorCorreo(String correoElectronico) {
            return Optional.ofNullable(porCorreo.get(correoElectronico.trim().toLowerCase()));
        }

        @Override
        public Optional<Cuenta> buscarPorCorreo(Connection conexion, String correoElectronico) {
            return buscarPorCorreo(correoElectronico);
        }

        private Map<String, Cuenta> copia() {
            return new HashMap<>(porCorreo);
        }

        private int secuenciaActual() {
            return secuencia;
        }

        private void restaurar(Map<String, Cuenta> copia, int secuenciaAnterior) {
            porCorreo.clear();
            porCorreo.putAll(copia);
            secuencia = secuenciaAnterior;
        }

        @Override
        public boolean existeCorreo(String correoElectronico) {
            if (correoElectronico == null) {
                return false;
            }
            return porCorreo.containsKey(correoElectronico.trim().toLowerCase());
        }

        @Override
        public long contarCuentas() {
            return porCorreo.size();
        }

        private void bloquear(String correoElectronico) {
            Cuenta cuenta = porCorreo.get(correoElectronico.trim().toLowerCase());
            if (cuenta != null) {
                cuenta.setEstado(EstadoCuenta.BLOQUEADA);
            }
        }
    }

    private static final class VinculoRepositorioFalso implements ICuentaNegocioRepositorio {
        private final List<VinculoCuentaNegocio> vinculos = new ArrayList<>();

        @Override
        public void guardar(VinculoCuentaNegocio vinculo) {
            guardar(null, vinculo);
        }

        @Override
        public void guardar(Connection conexion, VinculoCuentaNegocio vinculo) {
            Optional<VinculoCuentaNegocio> actual =
                    buscarPorCuentaYNegocio(vinculo.getIdCuenta(), vinculo.getIdNegocio());
            if (actual.isPresent()) {
                if (actual.get().estaActivo()) {
                    throw new IllegalStateException("La cuenta ya está vinculada a este negocio.");
                }
                Optional<VinculoCuentaNegocio> porCliente =
                        buscarPorNegocioYCliente(null, vinculo.getIdNegocio(), vinculo.getIdCliente());
                if (porCliente.isPresent() && porCliente.get().getIdCuenta() != vinculo.getIdCuenta()) {
                    throw new IllegalStateException("El perfil de cliente ya está vinculado a otra cuenta.");
                }
                actual.get().setIdCliente(vinculo.getIdCliente());
                actual.get().setRol(vinculo.getRol());
                actual.get().setEstado(EstadoCuenta.ACTIVA);
                return;
            }
            if (buscarPorNegocioYCliente(null, vinculo.getIdNegocio(), vinculo.getIdCliente()).isPresent()) {
                throw new IllegalStateException("El perfil de cliente ya está vinculado a otra cuenta.");
            }
            vinculos.add(vinculo);
        }

        @Override
        public Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(long idCuenta, long idNegocio) {
            for (VinculoCuentaNegocio vinculo : vinculos) {
                if (vinculo.getIdCuenta() == idCuenta && vinculo.getIdNegocio() == idNegocio) {
                    return Optional.of(vinculo);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<VinculoCuentaNegocio> buscarPorCuentaYNegocio(Connection conexion, long idCuenta,
                                                                     long idNegocio) {
            return buscarPorCuentaYNegocio(idCuenta, idNegocio);
        }

        @Override
        public Optional<VinculoCuentaNegocio> buscarPorNegocioYCliente(Connection conexion, long idNegocio,
                                                                      int idCliente) {
            for (VinculoCuentaNegocio vinculo : vinculos) {
                if (vinculo.getIdNegocio() == idNegocio && vinculo.getIdCliente() == idCliente) {
                    return Optional.of(vinculo);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<VinculoCuentaNegocio> listarPorCuenta(long idCuenta) {
            List<VinculoCuentaNegocio> propios = new ArrayList<>();
            for (VinculoCuentaNegocio vinculo : vinculos) {
                if (vinculo.getIdCuenta() == idCuenta) {
                    propios.add(vinculo);
                }
            }
            return propios;
        }

        @Override
        public boolean existeVinculo(long idCuenta, long idNegocio) {
            Optional<VinculoCuentaNegocio> vinculo = buscarPorCuentaYNegocio(idCuenta, idNegocio);
            return vinculo.isPresent() && vinculo.get().estaActivo();
        }

        private List<VinculoCuentaNegocio> copia() {
            return new ArrayList<>(vinculos);
        }

        private void restaurar(List<VinculoCuentaNegocio> copia) {
            vinculos.clear();
            vinculos.addAll(copia);
        }

        private void bloquear(long idCuenta, long idNegocio) {
            Optional<VinculoCuentaNegocio> vinculo = buscarPorCuentaYNegocio(idCuenta, idNegocio);
            if (vinculo.isPresent()) {
                vinculo.get().setEstado(EstadoCuenta.BLOQUEADA);
            }
        }

        private void forzar(long idCuenta, long idNegocio, int idCliente, RolUsuario rol) {
            guardar(new VinculoCuentaNegocio(idCuenta, idNegocio, idCliente, rol));
        }
    }

    private static final class NegocioRepositorioFalso implements INegocioRepository {
        private final Map<Long, Negocio> negocios = new HashMap<>();

        private NegocioRepositorioFalso() {
            negocios.put(1L, new Negocio(1L, "Tienda Uno", "uno", "uno@mail.com", true,
                    java.time.LocalDateTime.now(), null));
            negocios.put(2L, new Negocio(2L, "Tienda Dos", "dos", "dos@mail.com", true,
                    java.time.LocalDateTime.now(), null));
        }

        private void desactivar(long idNegocio) {
            Negocio actual = negocios.get(idNegocio);
            if (actual != null) {
                negocios.put(idNegocio, new Negocio(actual.getId(), actual.getNombre(),
                        actual.getSubdominio(), actual.getEmailContacto(), false,
                        actual.getFechaAlta(), java.time.LocalDateTime.now()));
            }
        }

        @Override
        public Optional<Negocio> buscarPorSubdominio(String subdominio) {
            for (Negocio negocio : negocios.values()) {
                if (negocio.getSubdominio().equalsIgnoreCase(subdominio)) {
                    return Optional.of(negocio);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Negocio> buscarPorId(long id) {
            return Optional.ofNullable(negocios.get(id));
        }

        @Override
        public List<Negocio> listarActivos() {
            return new ArrayList<>(negocios.values());
        }

        @Override
        public Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(long idNegocio) {
            return new HashMap<>();
        }

        @Override
        public Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave) {
            return Optional.empty();
        }

        @Override
        public void guardarOActualizarValorConfiguracion(long idNegocio, long idConfiguracion, String valor) {
        }
    }

    private static final class ClienteRepositorioFalso implements IClienteRepositorio {
        private final Map<Integer, Cliente> clientes = new HashMap<>();
        private int secuencia = 10;

        private void agregar(Cliente cliente) {
            clientes.put(cliente.getId(), cliente);
        }

        @Override
        public void guardar(Cliente cliente) {
            if (cliente.getId() == 0) {
                cliente.setId(secuencia++);
            }
            clientes.put(cliente.getId(), cliente);
        }

        @Override
        public void guardar(Connection conexion, Cliente cliente) {
            guardar(cliente);
        }

        @Override
        public Optional<Cliente> buscarPorDniParaActualizar(Connection conexion, long idNegocio, String dni) {
            return buscarPorDni(idNegocio, dni);
        }

        private Map<Integer, Cliente> copia() {
            return new HashMap<>(clientes);
        }

        private int secuenciaActual() {
            return secuencia;
        }

        private void restaurar(Map<Integer, Cliente> copia, int secuenciaAnterior) {
            clientes.clear();
            clientes.putAll(copia);
            secuencia = secuenciaAnterior;
        }

        @Override
        public Optional<Cliente> buscarPorId(long idNegocio, int id) {
            Cliente cliente = clientes.get(id);
            if (cliente != null && cliente.getIdNegocio() == idNegocio) {
                return Optional.of(cliente);
            }
            return Optional.empty();
        }

        @Override
        public Optional<Cliente> buscarPorDni(long idNegocio, String dni) {
            for (Cliente cliente : clientes.values()) {
                if (dni.equals(cliente.getDni()) && cliente.getIdNegocio() == idNegocio) {
                    return Optional.of(cliente);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<Cliente> listarTodos(long idNegocio) {
            return new ArrayList<>(clientes.values());
        }

        @Override
        public void actualizar(long idNegocio, int id, Cliente cliente) {
            clientes.put(id, cliente);
        }

        @Override
        public void eliminar(long idNegocio, int id) {
            clientes.remove(id);
        }
    }

    private static final class TransaccionFalsa implements ITransaccion {
        private final CuentaRepositorioFalso cuentas;
        private final ClienteRepositorioFalso clientes;
        private final VinculoRepositorioFalso vinculos;

        private TransaccionFalsa(CuentaRepositorioFalso cuentas, ClienteRepositorioFalso clientes,
                                 VinculoRepositorioFalso vinculos) {
            this.cuentas = cuentas;
            this.clientes = clientes;
            this.vinculos = vinculos;
        }

        @Override
        public void ejecutarTransaccion(OperacionTransaccional operacion) {
            Map<String, Cuenta> cuentasAntes = cuentas.copia();
            Map<Integer, Cliente> clientesAntes = clientes.copia();
            List<VinculoCuentaNegocio> vinculosAntes = vinculos.copia();
            int secuenciaCuentas = cuentas.secuenciaActual();
            int secuenciaClientes = clientes.secuenciaActual();
            try {
                operacion.ejecutar(null);
            } catch (Exception error) {
                cuentas.restaurar(cuentasAntes, secuenciaCuentas);
                clientes.restaurar(clientesAntes, secuenciaClientes);
                vinculos.restaurar(vinculosAntes);
                if (error instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new RuntimeException(error);
            }
        }
    }

    private static final class EjecutorDirecto implements IServiceExecutor {
        @Override
        public CompletableFuture<Void> ejecutar(Runnable tarea) {
            tarea.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> ejecutar(Callable<T> tarea) {
            try {
                return CompletableFuture.completedFuture(tarea.call());
            } catch (Exception e) {
                CompletableFuture<T> fallido = new CompletableFuture<>();
                fallido.completeExceptionally(e);
                return fallido;
            }
        }

        @Override
        public int getColaActual() {
            return 0;
        }

        @Override
        public int getHiloActivos() {
            return 0;
        }

        @Override
        public int getHilosTotales() {
            return 1;
        }

        @Override
        public TipoServicio getTipo() {
            return TipoServicio.AUTENTICACION;
        }

        @Override
        public void shutdown() {
        }
    }
}

