package com.CapaInfraestructura.Configuracion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Limpia datos sensibles antes de escribir mensajes en logs o consola. */
public final class SanitizadorLog {

    private static final Pattern CORREO =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PORTADOR =
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern CLAVE_VALOR = Pattern.compile(
            "(?i)(\"?(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|secret|"
                    + "password|contrasena|contraseña|api[_-]?key|authorization)\"?\\s*[:=]\\s*\"?)"
                    + "([^\\s\",}]+)");
    private static final Pattern POSIBLE_TARJETA =
            Pattern.compile("(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)");
    private static final int MAXIMO_CARACTERES = 500;

    private SanitizadorLog() {
    }

    public static String texto(String valor) {
        if (valor == null || valor.isEmpty()) {
            return valor;
        }
        String saneado = CORREO.matcher(valor).replaceAll("[correo]");
        saneado = PORTADOR.matcher(saneado).replaceAll("Bearer [oculto]");
        saneado = JWT.matcher(saneado).replaceAll("[token]");
        saneado = CLAVE_VALOR.matcher(saneado).replaceAll("$1[oculto]");
        saneado = enmascararTarjetas(saneado);
        return saneado.length() > MAXIMO_CARACTERES
                ? saneado.substring(0, MAXIMO_CARACTERES) + "..."
                : saneado;
    }

    public static String linea(String valor) {
        String saneado = texto(valor);
        if (saneado == null || saneado.isEmpty()) {
            return saneado;
        }
        StringBuilder resultado = new StringBuilder(saneado.length());
        for (int indice = 0; indice < saneado.length(); indice++) {
            char caracter = saneado.charAt(indice);
            switch (caracter) {
                case '\r' -> resultado.append("\\r");
                case '\n' -> resultado.append("\\n");
                case '\t' -> resultado.append("\\t");
                case '\u2028' -> resultado.append("\\u2028");
                case '\u2029' -> resultado.append("\\u2029");
                default -> {
                    if (caracter < 0x20 || caracter == 0x7f) {
                        resultado.append('?');
                    } else {
                        resultado.append(caracter);
                    }
                }
            }
        }
        return resultado.length() > MAXIMO_CARACTERES
                ? resultado.substring(0, MAXIMO_CARACTERES) + "..."
                : resultado.toString();
    }

    public static String error(Throwable error) {
        if (error == null) {
            return "error desconocido";
        }
        String mensaje = linea(error.getMessage());
        return error.getClass().getSimpleName()
                + (mensaje == null || mensaje.isBlank() ? "" : ": " + mensaje);
    }

    private static String enmascararTarjetas(String valor) {
        Matcher matcher = POSIBLE_TARJETA.matcher(valor);
        StringBuilder resultado = new StringBuilder();
        while (matcher.find()) {
            String candidato = matcher.group();
            String digitos = candidato.replace(" ", "").replace("-", "");
            String reemplazo = esNumeroTarjeta(digitos) ? "[tarjeta]" : candidato;
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(reemplazo));
        }
        matcher.appendTail(resultado);
        return resultado.toString();
    }

    private static boolean esNumeroTarjeta(String digitos) {
        if (digitos.length() < 13 || digitos.length() > 19) {
            return false;
        }
        int suma = 0;
        boolean duplicar = false;
        for (int indice = digitos.length() - 1; indice >= 0; indice--) {
            int digito = digitos.charAt(indice) - '0';
            if (digito < 0 || digito > 9) {
                return false;
            }
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }
}
