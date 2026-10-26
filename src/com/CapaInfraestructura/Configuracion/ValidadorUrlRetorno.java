package com.CapaInfraestructura.Configuracion;

import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Valida URLs de retorno de pasarelas contra una allowlist de orígenes. */
public final class ValidadorUrlRetorno {
    private static final String MARCADOR_SESION = "{CHECKOUT_SESSION_ID}";
    private static final String REEMPLAZO_SESION = "__CHECKOUT_SESSION_ID__";

    private ValidadorUrlRetorno() {
    }

    public static String validar(
            String url,
            String etiqueta,
            String origenesPermitidos,
            boolean produccion) {
        if (url == null || url.isBlank()) {
            throw new PasarelaNoDisponibleException(
                    "Falta la URL de retorno de " + etiqueta + ".");
        }

        URI uri = parsear(url.trim().replace(MARCADOR_SESION, REEMPLAZO_SESION), etiqueta);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute()
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || host == null
                || host.isBlank()
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new PasarelaNoDisponibleException(
                    "La URL de retorno de " + etiqueta + " debe ser absoluta, sin credenciales ni fragmento.");
        }

        boolean seguro = "https".equalsIgnoreCase(uri.getScheme());
        boolean local = esHostLocal(host);
        if (produccion && (!seguro || local)) {
            throw new PasarelaNoDisponibleException(
                    "La URL de retorno de " + etiqueta + " debe ser HTTPS y accesible públicamente.");
        }
        if (!produccion && !seguro && !local) {
            throw new PasarelaNoDisponibleException(
                    "La URL de retorno de " + etiqueta + " debe usar HTTPS fuera de localhost.");
        }

        List<String> origenes = parsearOrigenes(origenesPermitidos, etiqueta);
        if (produccion && origenes.isEmpty()) {
            throw new PasarelaNoDisponibleException(
                    "Configure stripe.allowed-return-origins con los orígenes permitidos para "
                            + etiqueta + ".");
        }
        if (!origenes.isEmpty() && !coincideOrigen(uri, origenes)) {
            throw new PasarelaNoDisponibleException(
                    "El origen de la URL de retorno de " + etiqueta
                            + " no está autorizado. Revise stripe.allowed-return-origins.");
        }

        return url.trim();
    }

    private static URI parsear(String url, String etiqueta) {
        try {
            return new URI(url);
        } catch (URISyntaxException exception) {
            throw new PasarelaNoDisponibleException(
                    "La URL de retorno de " + etiqueta + " no tiene un formato válido.",
                    exception);
        }
    }

    private static List<String> parsearOrigenes(String origenesPermitidos, String etiqueta) {
        List<String> origenes = new ArrayList<>();
        if (origenesPermitidos == null || origenesPermitidos.isBlank()) {
            return origenes;
        }

        for (String origen : origenesPermitidos.split(",")) {
            String valor = origen.trim();
            if (valor.isEmpty()) {
                continue;
            }
            URI uri = parsear(valor, etiqueta);
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new PasarelaNoDisponibleException(
                        "stripe.allowed-return-origins contiene un origen inválido: " + valor);
            }
            origenes.add(uri.getScheme().toLowerCase(Locale.ROOT)
                    + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + ":" + puerto(uri));
        }
        return origenes;
    }

    private static boolean coincideOrigen(URI uri, List<String> origenes) {
        String origenUrl = uri.getScheme().toLowerCase(Locale.ROOT)
                + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + ":" + puerto(uri);
        return origenes.contains(origenUrl);
    }

    private static int puerto(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean esHostLocal(String host) {
        if ("localhost".equals(host)
                || host.endsWith(".localhost")
                || "0.0.0.0".equals(host)
                || "[::]".equals(host)) {
            return true;
        }
        if (!host.matches("[0-9a-f:.\\[\\]]+")) {
            return false;
        }

        try {
            InetAddress direccion = InetAddress.getByName(host);
            return direccion.isLoopbackAddress()
                    || direccion.isAnyLocalAddress()
                    || direccion.isLinkLocalAddress()
                    || direccion.isSiteLocalAddress();
        } catch (UnknownHostException exception) {
            return true;
        }
    }
}
