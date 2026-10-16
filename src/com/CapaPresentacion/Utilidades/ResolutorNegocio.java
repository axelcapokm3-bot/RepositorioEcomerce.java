package com.CapaPresentacion.Utilidades;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.Negocio;
import com.sun.net.httpserver.HttpExchange;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resuelve el negocio (tenant) de una solicitud HTTP. */
public final class ResolutorNegocio {
    public static final String HEADER_NEGOCIO = "X-Negocio-Subdominio";

    private static volatile INegocioRepository repositorio;
    private static final Map<String, Long> CACHE = new ConcurrentHashMap<>();

    private ResolutorNegocio() {
    }

    public static void inicializar(INegocioRepository repo) {
        repositorio = repo;
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
        String clave = subdominio.trim().toLowerCase();
        Long cached = CACHE.get(clave);
        if (cached != null) {
            return Optional.of(cached);
        }
        INegocioRepository repo = repositorio;
        if (repo == null) {
            return Optional.empty();
        }
        Optional<Negocio> negocio = repo.buscarPorSubdominio(clave);
        if (negocio.isEmpty() || !negocio.get().isActivo() || negocio.get().getFechaBaja() != null) {
            return Optional.empty();
        }
        CACHE.put(clave, negocio.get().getId());
        return Optional.of(negocio.get().getId());
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
