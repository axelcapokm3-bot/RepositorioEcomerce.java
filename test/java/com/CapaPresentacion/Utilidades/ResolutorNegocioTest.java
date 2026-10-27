package com.CapaPresentacion.Utilidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Excepciones.CacheNoDisponibleException;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.RepositorioCacheTenant;
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
    private RepositorioCacheTenant cache;
    private ResolutorNegocio resolutor;

    @BeforeEach
    void setUp() {
        System.clearProperty(CLAVE_TTL);
        repositorio = new NegocioRepositorioFalso();
        repositorio.registrar(negocio(10L, true));
        cache = mock(RepositorioCacheTenant.class);
        resolutor = new ResolutorNegocio(repositorio, cache);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(CLAVE_TTL);
    }

    @Test
    @DisplayName("usa el ID de la cache sin consultar MySQL")
    void usaCacheSinConsultarMySql() {
        when(cache.obtener(SUBDOMINIO)).thenReturn("10");

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        assertEquals(0, repositorio.busquedas);
        verify(cache, never()).guardar(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("en cache miss consulta MySQL y guarda el ID")
    void cacheMissConsultaMySqlYGuarda() {
        when(cache.obtener(SUBDOMINIO)).thenReturn(null);

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        assertEquals(1, repositorio.busquedas);
        verify(cache).guardar(SUBDOMINIO, "10", 60L);
    }

    @Test
    @DisplayName("si Redis falla al leer, hace fallback a MySQL")
    void falloLecturaRedisHaceFallback() {
        when(cache.obtener(SUBDOMINIO))
                .thenThrow(new CacheNoDisponibleException("Redis caido", null));

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        assertEquals(1, repositorio.busquedas);
        verify(cache).guardar(SUBDOMINIO, "10", 60L);
    }

    @Test
    @DisplayName("si Redis falla al guardar, igual retorna el negocio de MySQL")
    void falloEscrituraRedisNoRompe() {
        when(cache.obtener(SUBDOMINIO)).thenReturn(null);
        doThrow(new CacheNoDisponibleException("Redis caido", null))
                .when(cache).guardar(anyString(), anyString(), anyLong());

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        assertEquals(1, repositorio.busquedas);
    }

    @Test
    @DisplayName("no cachea negocios inactivos")
    void noCacheaInactivos() {
        repositorio.registrar(negocio(10L, false));

        assertTrue(resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)).isEmpty());
        assertEquals(1, repositorio.busquedas);
        verify(cache, never()).guardar(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("un ID invalido en la cache se invalida y se consulta MySQL")
    void idInvalidoEnCacheHaceFallbackEInvalida() {
        when(cache.obtener(SUBDOMINIO)).thenReturn("no-es-un-id");

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        verify(cache).invalidar(SUBDOMINIO);
        verify(cache).guardar(SUBDOMINIO, "10", 60L);
    }

    @Test
    @DisplayName("invalida el subdominio normalizado")
    void invalidaSubdominioNormalizado() {
        resolutor.invalidar(" Tienda ");

        verify(cache).invalidar(SUBDOMINIO);
    }

    @Test
    @DisplayName("invalida por ID consultando el subdominio en MySQL")
    void invalidaPorIdConSubdominioDeMySql() {
        resolutor.invalidarPorId("10");

        assertEquals(1, repositorio.busquedasPorId);
        verify(cache).invalidar(SUBDOMINIO);
    }

    @Test
    @DisplayName("un ID invalido no consulta MySQL ni la cache")
    void idInvalidoNoConsulta() {
        resolutor.invalidarPorId("abc");

        assertEquals(0, repositorio.busquedasPorId);
        verify(cache, never()).invalidar(anyString());
    }

    @Test
    @DisplayName("usa el TTL configurado en segundos")
    void usaTtlConfigurado() {
        System.setProperty(CLAVE_TTL, "1");
        when(cache.obtener(SUBDOMINIO)).thenReturn(null);

        assertEquals(Optional.of(10L), resolutor.resolver(exchangeConSubdominio(SUBDOMINIO)));
        verify(cache).guardar(SUBDOMINIO, "10", 1L);
    }

    @Test
    @DisplayName("un encabezado invalido no cae al subdominio del host")
    void encabezadoInvalidoNoUsaHost() {
        HttpExchange exchange = exchangeConSubdominio("../tienda");
        Headers headers = exchange.getRequestHeaders();
        headers.set("Host", "tienda.example.com");

        assertTrue(resolutor.tieneIndicador(exchange));
        assertTrue(resolutor.resolver(exchange).isEmpty());
    }

    @Test
    @DisplayName("valida el formato del subdominio")
    void validaFormatoSubdominio() {
        assertTrue(resolutor.normalizarSubdominio("Tienda-1").isPresent());
        assertTrue(resolutor.normalizarSubdominio("-tienda").isEmpty());
        assertTrue(resolutor.normalizarSubdominio("tienda_1").isEmpty());
        assertTrue(resolutor.normalizarSubdominio("a".repeat(64)).isEmpty());
    }

    private Negocio negocio(long id, boolean activo) {
        return new Negocio(id, "Tienda", SUBDOMINIO, "tienda@mail.com", activo, null, null);
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
        private final Map<Long, Negocio> negociosPorId = new HashMap<>();
        private int busquedas;
        private int busquedasPorId;

        void registrar(Negocio negocio) {
            negocios.put(negocio.getSubdominio(), negocio);
            negociosPorId.put(negocio.getId(), negocio);
        }

        @Override
        public Optional<Negocio> buscarPorSubdominio(String subdominio) {
            busquedas++;
            return Optional.ofNullable(negocios.get(subdominio));
        }

        @Override
        public Optional<Negocio> buscarPorId(long id) {
            busquedasPorId++;
            return Optional.ofNullable(negociosPorId.get(id));
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
