package com.CapaPresentacion.Utilidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolutorNegocioTest {
    private static final String SUBDOMINIO = "tienda";
    private static final String CLAVE_TTL = "negocio.cache.ttl-segundos";

    private NegocioRepositorioFalso repositorio;

    @BeforeEach
    void setUp() {
        System.clearProperty(CLAVE_TTL);
        repositorio = new NegocioRepositorioFalso();
        repositorio.registrar(new Negocio(
                10L, "Tienda", SUBDOMINIO, "tienda@mail.com", true, null, null));
        ResolutorNegocio.inicializar(repositorio);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(CLAVE_TTL);
        ResolutorNegocio.inicializar(null);
    }

    @Test
    @DisplayName("usa el caché hasta que se invalida explícitamente")
    void cacheaHastaInvalidar() {
        HttpExchange exchange = exchangeConSubdominio(SUBDOMINIO);

        assertEquals(Optional.of(10L), ResolutorNegocio.resolver(exchange));
        repositorio.registrar(new Negocio(
                10L, "Tienda", SUBDOMINIO, "tienda@mail.com", false, null, null));

        assertEquals(Optional.of(10L), ResolutorNegocio.resolver(exchange),
                "El valor cacheado debe mantenerse dentro del TTL");
        ResolutorNegocio.invalidar(SUBDOMINIO);

        assertTrue(ResolutorNegocio.resolver(exchange).isEmpty());
    }

    @Test
    @DisplayName("el TTL obliga a releer el estado del negocio")
    void expiraPorTtl() throws InterruptedException {
        System.setProperty(CLAVE_TTL, "1");
        HttpExchange exchange = exchangeConSubdominio(SUBDOMINIO);

        assertEquals(Optional.of(10L), ResolutorNegocio.resolver(exchange));
        repositorio.registrar(new Negocio(
                10L, "Tienda", SUBDOMINIO, "tienda@mail.com", false, null, null));
        Thread.sleep(1200);

        assertTrue(ResolutorNegocio.resolver(exchange).isEmpty());
    }

    @Test
    @DisplayName("no cachea negocios inactivos")
    void noCacheaInactivos() {
        repositorio.registrar(new Negocio(
                10L, "Tienda", SUBDOMINIO, "tienda@mail.com", false, null, null));
        HttpExchange exchange = exchangeConSubdominio(SUBDOMINIO);

        assertTrue(ResolutorNegocio.resolver(exchange).isEmpty());
        assertTrue(ResolutorNegocio.resolver(exchange).isEmpty());
        assertEquals(2, repositorio.busquedas);
    }

    private HttpExchange exchangeConSubdominio(String subdominio) {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.set(ResolutorNegocio.HEADER_NEGOCIO, subdominio);
        when(exchange.getRequestHeaders()).thenReturn(headers);
        return exchange;
    }

    private static final class NegocioRepositorioFalso implements INegocioRepository {
        private final Map<String, Negocio> negocios = new HashMap<>();
        private int busquedas;

        void registrar(Negocio negocio) {
            negocios.put(negocio.getSubdominio(), negocio);
        }

        @Override
        public Optional<Negocio> buscarPorSubdominio(String subdominio) {
            busquedas++;
            return Optional.ofNullable(negocios.get(subdominio));
        }

        @Override
        public Optional<Negocio> buscarPorId(long id) {
            throw new UnsupportedOperationException();
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
}
