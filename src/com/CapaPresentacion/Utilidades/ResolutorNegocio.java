package com.CapaPresentacion.Utilidades;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.RepositorioCacheTenant;
import com.CapaDominio.Entidades.Negocio;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.sun.net.httpserver.HttpExchange;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Resuelve el negocio (tenant) de una solicitud HTTP. */
public final class ResolutorNegocio {
    public static final String HEADER_NEGOCIO = "X-Negocio-Subdominio";
    private static final Logger LOGGER = Logger.getLogger(ResolutorNegocio.class.getName());
    private static final String CLAVE_TTL = "negocio.cache.ttl-segundos";
    private static final long TTL_PREDETERMINADO_SEGUNDOS = 60L;
    private static final Pattern PATRON_SUBDOMINIO =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final INegocioRepository repositorio;
    private final RepositorioCacheTenant cache;

    public ResolutorNegocio(INegocioRepository repositorio, RepositorioCacheTenant cache) {
        if (repositorio == null) {
            throw new IllegalArgumentException("El repositorio de negocios es obligatorio.");
        }
        if (cache == null) {
            throw new IllegalArgumentException("La cache de tenant es obligatoria.");
        }
        this.repositorio = repositorio;
        this.cache = cache;
    }

    public Optional<Long> resolver(HttpExchange exchange) {
        String subdominio = exchange.getRequestHeaders().getFirst(HEADER_NEGOCIO);
        if (subdominio != null && !subdominio.isBlank()) {
            return resolverSubdominio(normalizarSubdominio(subdominio));
        }
        return resolverSubdominio(normalizarSubdominio(subdominioDeHost(exchange)));
    }

    /** Indica si la solicitud trae el encabezado explícito de negocio. */
    public boolean tieneIndicador(HttpExchange exchange) {
        String subdominio = exchange.getRequestHeaders().getFirst(HEADER_NEGOCIO);
        return subdominio != null && !subdominio.isBlank();
    }

    public void invalidar(String subdominio) {
        Optional<String> normalizado = normalizarSubdominio(subdominio);
        if (normalizado.isEmpty()) {
            return;
        }
        try {
            cache.invalidar(normalizado.get());
        } catch (RuntimeException e) {
            LOGGER.warning("No se pudo invalidar la cache de tenant; se continua sin cache: "
                    + e.getMessage());
        }
    }

    public void invalidarPorId(String idNegocio) {
        Long id = parsearId(idNegocio);
        if (id == null) {
            return;
        }
        Optional<Negocio> negocio;
        try {
            negocio = repositorio.buscarPorId(id);
        } catch (RuntimeException e) {
            LOGGER.warning("No se pudo consultar el negocio para invalidar la cache de tenant: "
                    + e.getMessage());
            return;
        }
        if (negocio.isPresent()) {
            invalidar(negocio.get().getSubdominio());
        }
    }

    private Optional<Long> resolverSubdominio(Optional<String> subdominio) {
        if (subdominio.isEmpty()) {
            return Optional.empty();
        }
        String clave = subdominio.get();
        Optional<Long> cacheado = obtenerDeCache(clave);
        if (cacheado.isPresent()) {
            return cacheado;
        }
        Optional<Negocio> negocio = repositorio.buscarPorSubdominio(clave);
        if (negocio.isEmpty() || !negocio.get().estaOperativo()) {
            return Optional.empty();
        }
        Long idNegocio = negocio.get().getId();
        guardarEnCache(clave, idNegocio);
        return Optional.of(idNegocio);
    }

    private Optional<Long> obtenerDeCache(String subdominio) {
        String valor;
        try {
            valor = cache.obtener(subdominio);
        } catch (RuntimeException e) {
            LOGGER.warning("Redis no disponible al leer el tenant; se consulta MySQL: "
                    + e.getMessage());
            return Optional.empty();
        }
        Long id = parsearId(valor);
        if (id != null) {
            return Optional.of(id);
        }
        if (valor != null && !valor.isBlank()) {
            LOGGER.warning("La cache de tenant contiene un id invalido; se invalida la entrada.");
            invalidar(subdominio);
        }
        return Optional.empty();
    }

    private void guardarEnCache(String subdominio, long idNegocio) {
        try {
            cache.guardar(subdominio, String.valueOf(idNegocio), ttlSegundos());
        } catch (RuntimeException e) {
            LOGGER.warning("No se pudo guardar el tenant en cache; se continua con MySQL: "
                    + e.getMessage());
        }
    }

    private Long parsearId(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(valor.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    Optional<String> normalizarSubdominio(String subdominio) {
        if (subdominio == null || subdominio.isBlank()) {
            return Optional.empty();
        }
        String normalizado = subdominio.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 63 || !PATRON_SUBDOMINIO.matcher(normalizado).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalizado);
    }

    private long ttlSegundos() {
        String configurado = ConfigLoader.getProperty(CLAVE_TTL);
        if (configurado != null && !configurado.trim().isEmpty()) {
            try {
                long segundos = Long.parseLong(configurado.trim());
                if (segundos > 0) {
                    return segundos;
                }
            } catch (NumberFormatException exception) {
                return TTL_PREDETERMINADO_SEGUNDOS;
            }
        }
        return TTL_PREDETERMINADO_SEGUNDOS;
    }

    private String subdominioDeHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return null;
        }
        int dosPuntos = host.indexOf(':');
        if (dosPuntos >= 0) {
            host = host.substring(0, dosPuntos);
        }
        host = host.trim().toLowerCase(Locale.ROOT);
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
