package com.CapaPresentacion.Utilidades;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.Negocio;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.sun.net.httpserver.HttpExchange;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resuelve el negocio (tenant) de una solicitud HTTP. */
public final class ResolutorNegocio {
    public static final String HEADER_NEGOCIO = "X-Negocio-Subdominio";
    private static final String CLAVE_TTL = "negocio.cache.ttl-segundos";
    private static final long TTL_PREDETERMINADO_SEGUNDOS = 60L;

    private record EntradaCache(long idNegocio, long expiraEn) {
    }

    private static volatile INegocioRepository repositorio;
    private static final Map<String, EntradaCache> CACHE = new ConcurrentHashMap<>();

    private ResolutorNegocio() {
    }

    public static void inicializar(INegocioRepository repo) {
        repositorio = repo;
        CACHE.clear();
    }

    public static void invalidar(String subdominio) {
        if (subdominio == null || subdominio.isBlank()) {
            return;
        }
        CACHE.remove(subdominio.trim().toLowerCase(Locale.ROOT));
    }

    public static void invalidarTodo() {
        CACHE.clear();
    }

    public static Optional<Long> resolver(HttpExchange exchange) {
        String subdominio = exchange.getRequestHeaders().getFirst(HEADER_NEGOCIO);
        if (subdominio == null || subdominio.isBlank()) {
            subdominio = subdominioDeHost(exchange);
        }
        if (subdominio == null || subdominio.isBlank()) {
            return Optional.empty();
        }
        String clave = subdominio.trim().toLowerCase(Locale.ROOT);
        long ahora = System.currentTimeMillis();
        EntradaCache cached = CACHE.get(clave);
        if (cached != null && cached.expiraEn() > ahora) {
            return Optional.of(cached.idNegocio());
        }
        if (cached != null) {
            CACHE.remove(clave, cached);
        }
        INegocioRepository repo = repositorio;
        if (repo == null) {
            return Optional.empty();
        }
        Optional<Negocio> negocio = repo.buscarPorSubdominio(clave);
        if (negocio.isEmpty() || !negocio.get().estaOperativo()) {
            return Optional.empty();
        }
        CACHE.put(clave, new EntradaCache(
                negocio.get().getId(), ahora + ttlMilisegundos()));
        return Optional.of(negocio.get().getId());
    }

    private static long ttlMilisegundos() {
        String configurado = ConfigLoader.getProperty(CLAVE_TTL);
        if (configurado != null && !configurado.trim().isEmpty()) {
            try {
                long segundos = Long.parseLong(configurado.trim());
                if (segundos > 0) {
                    return segundos * 1000L;
                }
            } catch (NumberFormatException exception) {
                return TTL_PREDETERMINADO_SEGUNDOS * 1000L;
            }
        }
        return TTL_PREDETERMINADO_SEGUNDOS * 1000L;
    }

    static String subdominioDeHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return null;
        }
        int dosPuntos = host.indexOf(':');
        if (dosPuntos >= 0) {
            host = host.substring(0, dosPuntos);
        }
        host = host.trim().toLowerCase();
        String[] partes = host.split("\\.");
        if (partes.length < 2) {
            return null;
        }
        String primero = partes[0];
        if (primero.isEmpty() || "www".equals(primero)) {
            return null;
        }
        return primero;
    }
}
