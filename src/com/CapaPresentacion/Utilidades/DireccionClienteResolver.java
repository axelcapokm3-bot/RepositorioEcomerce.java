package com.CapaPresentacion.Utilidades;

import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.sun.net.httpserver.HttpExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** Obtiene la dirección IP del cliente respetando proxies confiables configurados. */
public final class DireccionClienteResolver {

    public static final String CLAVE_PROXIES_CONFIABLES = "app.trusted-proxies";
    private static final String DESCONOCIDA = "desconocida";

    private DireccionClienteResolver() {
    }

    public static String resolver(HttpExchange exchange) {
        String remota = direccionRemota(exchange);
        if (DESCONOCIDA.equals(remota) || !esProxyConfiable(remota)) {
            return remota;
        }
        String reenviada = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String cliente = clienteDesdeReenvio(reenviada);
            if (cliente != null) {
                return cliente;
            }
        }
        String real = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (real != null) {
            String normalizada = real.trim();
            if (esDireccionIp(normalizada)) {
                return normalizada;
            }
        }
        return remota;
    }

    static String clienteDesdeReenvio(String encabezado) {
        String[] partes = encabezado.split(",");
        for (int indice = partes.length - 1; indice >= 0; indice--) {
            String candidata = partes[indice].trim();
            if (!esDireccionIp(candidata)) {
                continue;
            }
            if (!esProxyConfiable(candidata)) {
                return candidata;
            }
        }
        for (int indice = partes.length - 1; indice >= 0; indice--) {
            String candidata = partes[indice].trim();
            if (esDireccionIp(candidata)) {
                return candidata;
            }
        }
        return null;
    }

    static boolean esDireccionIp(String valor) {
        if (valor == null || valor.isBlank() || valor.length() > 45) {
            return false;
        }
        if (!valor.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        if (!valor.contains(":") && !valor.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        try {
            InetAddress.getByName(valor);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean esProxyConfiable(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return false;
        }
        for (String proxy : proxiesConfiables()) {
            if (proxy.equals(direccion)) {
                return true;
            }
        }
        return false;
    }

    static List<String> proxiesConfiables() {
        String configurados = ConfigLoader.getProperty(CLAVE_PROXIES_CONFIABLES);
        List<String> proxies = new ArrayList<>();
        if (configurados == null || configurados.isBlank()) {
            return proxies;
        }
        for (String candidato : configurados.split(",")) {
            String normalizado = candidato.trim();
            if (esDireccionIp(normalizado)) {
                proxies.add(normalizado);
            }
        }
        return proxies;
    }

    private static String direccionRemota(HttpExchange exchange) {
        InetSocketAddress remota = exchange.getRemoteAddress();
        if (remota == null || remota.getAddress() == null) {
            return DESCONOCIDA;
        }
        return remota.getAddress().getHostAddress();
    }
}
