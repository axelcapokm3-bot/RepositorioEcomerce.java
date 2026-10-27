package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.AutenticacionService;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaPresentacion.Utilidades.CacheTenantEnMemoria;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthControllerTenantTest {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    private AutenticacionService servicio;
    private ProveedorTokenFalso proveedor;
    private ResolutorNegocio resolutorNegocio;
    private HttpServer server;
    private String baseUrl;
    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @BeforeEach
    void iniciar() throws Exception {
        servicio = mock(AutenticacionService.class);
        when(servicio.cambiarNegocioAsync(any(Principal.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(sesion(TENANT_A)));
        when(servicio.vincularNegocioAsync(any(Principal.class), anyLong(), anyString(),
                anyString(), anyString(), any(LocalDate.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sesion(TENANT_A)));
        resolutorNegocio = new ResolutorNegocio(
                new NegocioRepositorioFalso(), new CacheTenantEnMemoria());
        proveedor = new ProveedorTokenFalso();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth",
                new AuthController(servicio, proveedor, resolutorNegocio));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("negocio-activo ignora idNegocio del body y usa el subdominio")
    void negocioActivoIgnoraIdNegocioDelBody() throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/api/auth/negocio-activo",
                "{\"idNegocio\":999}", "tenant-b");

        assertEquals(200, respuesta.statusCode());
        assertEquals(TENANT_B, capturarNegocioCambiado());
    }

    @Test
    @DisplayName("negocio-activo usa el negocio del JWT si no hay subdominio")
    void negocioActivoUsaNegocioDelJwtSinSubdominio() throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/api/auth/negocio-activo",
                "{\"idNegocio\":999}", null);

        assertEquals(200, respuesta.statusCode());
        assertEquals(TENANT_A, capturarNegocioCambiado());
    }

    @Test
    @DisplayName("vincular ignora idNegocio del body y usa el subdominio")
    void vincularIgnoraIdNegocioDelBody() throws Exception {
        String body = "{\"idNegocio\":999,\"dniCliente\":\"30987654\","
                + "\"nombreCliente\":\"Ana\",\"apellidoCliente\":\"Gomez\","
                + "\"fechaNacimientoCliente\":\"1992-03-10\","
                + "\"contraseniaPlana\":\"Segura123\"}";

        HttpResponse<String> respuesta = enviar("POST", "/api/auth/vincular",
                body, "tenant-b");

        assertEquals(200, respuesta.statusCode());
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(servicio).vincularNegocioAsync(any(Principal.class), captor.capture(),
                anyString(), anyString(), anyString(), any(LocalDate.class), anyString());
        assertEquals(TENANT_B, captor.getValue());
    }

    @Test
    @DisplayName("un subdominio inexistente no procesa el cambio de negocio")
    void subdominioInexistenteDevuelve400() throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/api/auth/negocio-activo",
                "{}", "tenant-inexistente");

        assertEquals(400, respuesta.statusCode());
        verify(servicio, never()).cambiarNegocioAsync(any(Principal.class), anyLong());
    }

    @Test
    @DisplayName("un subdominio inactivo no procesa la vinculación")
    void subdominioInactivoDevuelve400() throws Exception {
        String body = "{\"dniCliente\":\"30987654\",\"nombreCliente\":\"Ana\","
                + "\"apellidoCliente\":\"Gomez\",\"fechaNacimientoCliente\":\"1992-03-10\","
                + "\"contraseniaPlana\":\"Segura123\"}";

        HttpResponse<String> respuesta = enviar("POST", "/api/auth/vincular",
                body, "tenant-inactivo");

        assertEquals(400, respuesta.statusCode());
        verify(servicio, never()).vincularNegocioAsync(any(Principal.class), anyLong(),
                anyString(), anyString(), anyString(), any(LocalDate.class), anyString());
    }

    @Test
    @DisplayName("salida revoca el token y la sesion deja de ser valida")
    void salidaRevocaToken() throws Exception {
        HttpResponse<String> salida = enviar("POST", "/api/auth/salida", null, null);
        assertEquals(200, salida.statusCode());
        assertTrue(proveedor.revocados.contains("admin-a"));

        HttpResponse<String> sesion = enviar("GET", "/api/auth/sesion", null, null);
        assertEquals(401, sesion.statusCode());
    }

    private long capturarNegocioCambiado() {
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(servicio).cambiarNegocioAsync(any(Principal.class), captor.capture());
        return captor.getValue();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String body, String subdominio)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .header("Authorization", "Bearer admin-a")
                .header("Content-Type", "application/json");
        if (subdominio != null) {
            builder.header(ResolutorNegocio.HEADER_NEGOCIO, subdominio);
        }
        builder.method(metodo, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return clienteHttp.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static SesionResponseDTO sesion(long idNegocio) {
        return new SesionResponseDTO("token-" + idNegocio, "CLIENTE", 1L, 20, idNegocio, 3600L);
    }

    private static final class ProveedorTokenFalso implements IProveedorTokenAcceso {
        private final Set<String> revocados = new HashSet<>();

        @Override
        public String emitir(long idCuenta, int idCliente, long idNegocio, String rol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Principal> validar(String token) {
            if (!revocados.contains(token) && "admin-a".equals(token)) {
                return Optional.of(new Principal(1L, 10, TENANT_A, RolUsuario.ADMIN));
            }
            return Optional.empty();
        }

        @Override
        public void revocar(String token) {
            revocados.add(token);
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
                return Optional.of(negocio(TENANT_A, "Tenant A", "tenant-a", true));
            }
            if ("tenant-b".equalsIgnoreCase(subdominio)) {
                return Optional.of(negocio(TENANT_B, "Tenant B", "tenant-b", true));
            }
            if ("tenant-inactivo".equalsIgnoreCase(subdominio)) {
                return Optional.of(negocio(3L, "Tenant inactivo", "tenant-inactivo", false));
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

        private static Negocio negocio(long id, String nombre, String subdominio, boolean activo) {
            return new Negocio(id, nombre, subdominio, null, activo,
                    LocalDateTime.now().minusDays(1), null);
        }
    }
}
