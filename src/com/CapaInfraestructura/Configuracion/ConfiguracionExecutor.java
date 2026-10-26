package com.CapaInfraestructura.Configuracion;

import com.CapaAplicacion.Interfaces.TipoServicio;

import java.util.Locale;
import java.util.function.Function;

public final class ConfiguracionExecutor {

    private final int tamanoCore;
    private final int tamanoMax;
    private final int capacidadCola;

    private ConfiguracionExecutor(int tamanoCore, int tamanoMax, int capacidadCola) {
        this.tamanoCore = tamanoCore;
        this.tamanoMax = tamanoMax;
        this.capacidadCola = capacidadCola;
    }

    public static ConfiguracionExecutor para(TipoServicio tipo) {
        return para(tipo, ConfigLoader::getProperty);
    }

    static ConfiguracionExecutor para(TipoServicio tipo, Function<String, String> propiedades) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de servicio no puede ser nulo.");
        }
        if (propiedades == null) {
            throw new IllegalArgumentException("La fuente de configuración no puede ser nula.");
        }

        ConfiguracionExecutor predeterminada = predeterminada(tipo);
        String base = claveBase(tipo);
        int tamanoCore = entero(base + ".core", predeterminada.tamanoCore, propiedades);
        int tamanoMax = entero(base + ".max", predeterminada.tamanoMax, propiedades);
        int capacidadCola = entero(base + ".cola", predeterminada.capacidadCola, propiedades);

        validar(base, tamanoCore, tamanoMax, capacidadCola);
        return new ConfiguracionExecutor(tamanoCore, tamanoMax, capacidadCola);
    }

    public int tamanoCore() {
        return tamanoCore;
    }

    public int tamanoMax() {
        return tamanoMax;
    }

    public int capacidadCola() {
        return capacidadCola;
    }

    private static int entero(String clave, int predeterminado,
                              Function<String, String> propiedades) {
        String valor = propiedades.apply(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Valor inválido para " + clave + ": '" + valor + "'. Debe ser un entero.", e);
        }
    }

    private static void validar(String base, int tamanoCore, int tamanoMax, int capacidadCola) {
        if (tamanoCore < 0) {
            throw new IllegalStateException(base + ".core debe ser mayor o igual a 0 (valor: "
                    + tamanoCore + ").");
        }
        if (tamanoMax < 1) {
            throw new IllegalStateException(base + ".max debe ser mayor o igual a 1 (valor: "
                    + tamanoMax + ").");
        }
        if (tamanoCore > tamanoMax) {
            throw new IllegalStateException(base + ".core no puede superar a " + base
                    + ".max (" + tamanoCore + " > " + tamanoMax + ").");
        }
        if (capacidadCola < 1) {
            throw new IllegalStateException(base + ".cola debe ser mayor o igual a 1 (valor: "
                    + capacidadCola + ").");
        }
    }

    private static ConfiguracionExecutor predeterminada(TipoServicio tipo) {
        return switch (tipo) {
            case VENTA -> new ConfiguracionExecutor(5, 10, 100);
            case PRODUCTO -> new ConfiguracionExecutor(2, 5, 50);
            case CLIENTE -> new ConfiguracionExecutor(2, 2, 30);
            case AUDITORIA -> new ConfiguracionExecutor(1, 2, 20);
            case NOTIFICACIONES -> new ConfiguracionExecutor(1, 1, 50);
            case AUTENTICACION -> new ConfiguracionExecutor(1, 2, 50);
        };
    }

    private static String claveBase(TipoServicio tipo) {
        return "executor." + tipo.name().toLowerCase(Locale.ROOT);
    }
}
