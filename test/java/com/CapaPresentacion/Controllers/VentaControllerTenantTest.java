package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.VentaService;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpServer;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VentaControllerTenantTest {

    private static final long NEGOCIO = 1L;
    private static final int CLIENTE = 10;
    private static final int PRODUCTO = 5;

    private VentaService servicio;
    private NegocioRepositorioFalso negocios;
    private ProductoRepositorioFalso productos;
    private HttpServer server;
    private String baseUrl;
    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @BeforeEach
    void iniciar() throws Exception {
        servicio = mock(VentaService.class);
        negocios = new NegocioRepositorioFalso();
        productos = new ProductoRepositorioFalso();
        ResolutorNegocio.inicializar(negocios);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ventas", new VentaController(
                servicio, productos, mock(IVentaRepository.class),
                new ProveedorTokenFalso(), negocios));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        if (server != null) {
            server.stop(0);
        }
        ResolutorNegocio.inicializar(null);
    }

    @Test
    @DisplayName("crear venta rechaza un negocio inactivo")
    void rechazaNegocioInactivo() throws Exception {
        negocios.registrar(false);
        productos.registrar(true);

        HttpResponse<String> respuesta = enviar("crear");

        assertEquals(409, respuesta.statusCode());
        verify(servicio, never()).crearVentaConPagoAsync(
                anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crear venta rechaza un producto inactivo")
    void rechazaProductoInactivo() throws Exception {
        negocios.registrar(true);
        productos.registrar(false);

        HttpResponse<String> respuesta = enviar("crear");

        assertEquals(404, respuesta.statusCode());
        verify(servicio, never()).crearVentaConPagoAsync(
                anyLong(), any(), any(), any(), any());
    }

    private HttpResponse<String> enviar(String ruta) throws Exception {
        String body = "{\"idCliente\":" + CLIENTE
                + ",\"tipoPago\":\"MERCADO_PAGO\",\"items\":[{\"idProducto\":"
                + PRODUCTO + ",\"cantidad\":1}]}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ventas/" + ruta))
                .header("Authorization", "Bearer token-cliente")
                .header("Idempotency-Key", "clave-1")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return clienteHttp.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class ProveedorTokenFalso implements IProveedorTokenAcceso {
        @Override
        public String emitir(long idCuenta, int idCliente, long idNegocio, String rol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Principal> validar(String token) {
            if ("token-cliente".equals(token)) {
                return Optional.of(new Principal(1L, CLIENTE, NEGOCIO, RolUsuario.CLIENTE));
            }
            return Optional.empty();
        }

        @Override
        public long segundosDeVida() {
            return 3600L;
        }
    }

    private static final class NegocioRepositorioFalso implements INegocioRepository {
        private Negocio negocio;

        void registrar(boolean activo) {
            negocio = new Negocio(NEGOCIO, "Negocio", "negocio", "negocio@mail.com",
                    activo, null, null);
        }

        @Override
        public Optional<Negocio> buscarPorSubdominio(String subdominio) {
            return Optional.ofNullable(negocio);
        }

        @Override
        public Optional<Negocio> buscarPorId(long id) {
            return negocio != null && negocio.getId() == id
                    ? Optional.of(negocio) : Optional.empty();
        }

        @Override
        public List<Negocio> listarActivos() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(long idNegocio) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void guardarOActualizarValorConfiguracion(
                long idNegocio, long idConfiguracion, String valor) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ProductoRepositorioFalso implements IProductoRepository {
        private Producto producto;

        void registrar(boolean activo) {
            producto = new Producto("Producto", new BigDecimal("100"), 10, 0,
                    PRODUCTO, CategoriaProducto.ELECTRONICA);
            producto.setIdNegocio(NEGOCIO);
            producto.setActivo(activo);
        }

        @Override
        public void guardar(Producto producto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Producto> buscarPorId(long idNegocio, int id) {
            return producto != null && producto.getId() == id
                    ? Optional.of(producto) : Optional.empty();
        }

        @Override
        public List<Producto> listarTodos(long idNegocio) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizar(long idNegocio, int id, Producto producto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void eliminar(long idNegocio, int id) {
            throw new UnsupportedOperationException();
        }
    }
}
