package com.CapaPresentacion.Utilidades;

import com.sun.net.httpserver.HttpExchange;

/** Cabeceras de seguridad comunes a todas las respuestas HTTP del backend. */
public final class CabecerasSeguridad {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    private CabecerasSeguridad() {
    }

    public static void aplicar(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set(
                "Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }
}
