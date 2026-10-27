package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.ClienteService;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaInfraestructura.Implementacion.RepositorioCliente;
import com.CapaPresentacion.Utilidades.CacheTenantEnMemoria;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteControllerAislamientoTest {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    private HttpServer server;
    private String baseUrl;
    private RepositorioCliente repositorio;
    private Cliente clienteTenantB;
    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @BeforeEach
    void iniciar() throws Exception {
        repositorio = new RepositorioCliente();
        clienteTenantB = crearCliente("30222222", "Beto", "Benitez", TENANT_B);
        repositorio.guardar(crearCliente("30111111", "Ana", "Alvarez", TENANT_A));
        repositorio.guardar(clienteTenantB);
        ResolutorNegocio resolutorNegocio = new ResolutorNegocio(
                new NegocioRepositorioFalso(), new CacheTenantEnMemoria());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/clientes",
                new ClienteController(new ClienteService(repositorio), new ProveedorTokenFalso(),
                        resolutorNegocio));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        if (server != null) {
            server.stop(0);
        }
    }

    @AfterAll
    static void cerrarExecutors() {
        RegistroExecutors.cerrarTodos();
    }

    @Test
    @DisplayName("GET de un cliente de otro tenant responde 404 sin revelar su existencia")
    void getClienteDeOtroTenantDevuelve404() throws Exception {
        HttpResponse<String> respuesta = enviar("GET",
                "/api/clientes/" + clienteTenantB.getId(), null);

        assertEquals(404, respuesta.statusCode());
        assertTrue(repositorio.buscarPorId(TENANT_B, clienteTenantB.getId()).isPresent());
    }

    @Test
    @DisplayName("DELETE de un cliente de otro tenant responde 404 y no lo da de baja")
    void deleteClienteDeOtroTenantDevuelve404() throws Exception {
        HttpResponse<String> respuesta = enviar("DELETE",
                "/api/clientes/" + clienteTenantB.getId(), null);

        assertEquals(404, respuesta.statusCode());
        assertTrue(repositorio.buscarPorId(TENANT_B, clienteTenantB.getId()).orElseThrow().isActivo());
    }

    @Test
    @DisplayName("PUT de un cliente de otro tenant responde 404 y no lo modifica")
    void putClienteDeOtroTenantDevuelve404() throws Exception {
        String body = "{\"dni\":\"30222222\",\"nombre\":\"Intruso\",\"apellido\":\"Intruso\","
                + "\"direccion\":\"Calle 1\",\"fechaNacimiento\":\"1990-01-01\",\"localidad\":\"CABA\"}";

        HttpResponse<String> respuesta = enviar("PUT",
                "/api/clientes/" + clienteTenantB.getId(), body, null);

        assertEquals(404, respuesta.statusCode());
        assertEquals("Beto", repositorio.buscarPorId(TENANT_B, clienteTenantB.getId())
                .orElseThrow().getNombre());
    }

    @Test
    @DisplayName("un header de otro tenant no puede reemplazar el tenant del token")
    void headerDeOtroTenantDevuelve403() throws Exception {
        HttpResponse<String> respuesta = enviar("GET",
                "/api/clientes/" + clienteTenantB.getId(), "tenant-b");

        assertEquals(403, respuesta.statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String subdominio)
            throws Exception {
        return enviar(metodo, ruta, null, subdominio);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String body, String subdominio)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .header("Authorization", "Bearer admin-a")
                .header("Content-Type", "application/json");
        if (subdominio != null) {
            builder.header("X-Negocio-Subdominio", subdominio);
        }
        builder.method(metodo, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return clienteHttp.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Cliente crearCliente(String dni, String nombre, String apellido, long idNegocio) {
        Cliente cliente = new Cliente(0, dni, nombre, apellido, "Calle 1",
                LocalDate.of(1990, 1, 1), "CABA");
        cliente.setIdNegocio(idNegocio);
        return cliente;
    }

    private static final class ProveedorTokenFalso implements IProveedorTokenAcceso {
        @Override
        public String emitir(long idCuenta, int idCliente, long idNegocio, String rol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Principal> validar(String token) {
            if ("admin-a".equals(token)) {
                return Optional.of(new Principal(1L, 10, TENANT_A, RolUsuario.ADMIN));
            }
            return Optional.empty();
        }

        @Override
        public void revocar(String token) {
        }

        @Override
        public long segundosDeVida() {
            return 3600L;
        }
    }

    private static final class NegocioRepositorioFalso implements INegocioRepository {
        @Override
        public Optional<Negocio> buscarPorSubdominio(String subdominio) {
            if ("tenant-a".equalsIgnoreCase(subdominio)) {
                return Optional.of(new Negocio(TENANT_A, "Tenant A", "tenant-a", null, true, null, null));
            }
            if ("tenant-b".equalsIgnoreCase(subdominio)) {
                return Optional.of(new Negocio(TENANT_B, "Tenant B", "tenant-b", null, true, null, null));
            }
            return Optional.empty();
        }

        @Override
        public Optional<Negocio> buscarPorId(long id) {
            return Optional.empty();
        }

        @Override
        public List<Negocio> listarActivos() {
            return List.of();
        }

        @Override
        public Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(long idNegocio) {
            return Map.of();
        }

        @Override
        public Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave) {
            return Optional.empty();
        }

        @Override
        public void guardarOActualizarValorConfiguracion(long idNegocio, long idConfiguracion,
                                                         String valor) {
            throw new UnsupportedOperationException();
        }
    }
}
