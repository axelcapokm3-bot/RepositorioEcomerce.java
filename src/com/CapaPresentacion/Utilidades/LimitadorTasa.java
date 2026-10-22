package com.CapaPresentacion.Utilidades;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.CapaInfraestructura.Configuracion.ConfigLoader;

public final class LimitadorTasa {
    private static final long VENTANA_PREDETERMINADA_SEGUNDOS = 60L;
    private static final long LIMPIEZA_PREDETERMINADA_SEGUNDOS = 60L;
    private static final int MAX_CLAVES_PREDETERMINADO = 10_000;
    private static final Map<String, Ventana> VENTANAS = new ConcurrentHashMap<>();

    private static ScheduledExecutorService limpieza;

    private LimitadorTasa() {
    }

    public record Resultado(boolean excedido, long segundosParaReintentar) {
        public static Resultado permitido() {
            return new Resultado(false, 0L);
        }
    }

    public static Resultado evaluar(String clave, int maxPorVentana, long ventanaSegundos) {
        if (clave == null || clave.isBlank() || maxPorVentana <= 0) {
            return Resultado.permitido();
        }
        long ventanaMs = Math.max(1L, ventanaSegundos) * 1000L;
        long ahora = System.currentTimeMillis();
        Ventana ventana = VENTANAS.compute(clave, (k, actual) -> {
            if (actual == null || ahora - actual.inicio >= ventanaMs) {
                return new Ventana(ahora, 1, ventanaMs);
            }
            actual.usos++;
            actual.ventanaMs = ventanaMs;
            return actual;
        });
        mantenerCapacidad(ahora);
        if (ventana.usos > maxPorVentana) {
            long restanteMs = Math.max(1L, ventana.inicio + ventana.ventanaMs - ahora);
            long segundos = Math.max(1L, (restanteMs + 999L) / 1000L);
            return new Resultado(true, segundos);
        }
        return Resultado.permitido();
    }

    public static boolean excede(String clave, int maxPorMinuto) {
        return evaluar(clave, maxPorMinuto, ventanaSegundos("global")).excedido();
    }

    public static int limiteConfigurado(String ambito, int predeterminado) {
        return limiteConfigurado(ambito, "limite", predeterminado);
    }

    public static int limiteConfigurado(String ambito, String sufijo, int predeterminado) {
        if (ambito == null || ambito.isBlank()) {
            return predeterminado;
        }
        if (sufijo != null && !sufijo.isBlank()) {
            int especifico = entero("rate-limit." + ambito + "." + sufijo, -1);
            if (especifico > 0) {
                return especifico;
            }
        }
        return entero("rate-limit." + ambito + ".limite", predeterminado);
    }

    public static long ventanaSegundos(String ambito) {
        long global = segundos("rate-limit.ventana-segundos", VENTANA_PREDETERMINADA_SEGUNDOS);
        if (ambito == null || ambito.isBlank()) {
            return global;
        }
        return segundos("rate-limit." + ambito + ".ventana-segundos", global);
    }

    public static synchronized void iniciar() {
        if (limpieza != null) {
            return;
        }
        long intervalo = Math.max(5L,
                segundos("rate-limit.limpieza-segundos", LIMPIEZA_PREDETERMINADA_SEGUNDOS));
        limpieza = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "rate-limit-cleanup");
            hilo.setDaemon(true);
            return hilo;
        });
        limpieza.scheduleAtFixedRate(LimitadorTasa::limpiarExpiradas,
                intervalo, intervalo, TimeUnit.SECONDS);
    }

    public static synchronized void detener() {
        if (limpieza != null) {
            limpieza.shutdownNow();
            limpieza = null;
        }
        VENTANAS.clear();
    }

    static void limpiar() {
        VENTANAS.clear();
    }

    static int cantidadClaves() {
        return VENTANAS.size();
    }

    private static void mantenerCapacidad(long ahora) {
        int maximo = entero("rate-limit.max-claves", MAX_CLAVES_PREDETERMINADO);
        if (maximo <= 0 || VENTANAS.size() <= maximo) {
            return;
        }
        limpiarExpiradas(ahora);
        while (VENTANAS.size() > maximo) {
            String claveMasAntigua = null;
            long inicioMasAntiguo = Long.MAX_VALUE;
            for (Map.Entry<String, Ventana> entrada : VENTANAS.entrySet()) {
                if (entrada.getValue().inicio < inicioMasAntiguo) {
                    inicioMasAntiguo = entrada.getValue().inicio;
                    claveMasAntigua = entrada.getKey();
                }
            }
            if (claveMasAntigua == null || VENTANAS.remove(claveMasAntigua) == null) {
                return;
            }
        }
    }

    private static void limpiarExpiradas() {
        limpiarExpiradas(System.currentTimeMillis());
    }

    private static void limpiarExpiradas(long ahora) {
        for (Map.Entry<String, Ventana> entrada : VENTANAS.entrySet()) {
            Ventana ventana = entrada.getValue();
            if (ahora - ventana.inicio >= ventana.ventanaMs) {
                VENTANAS.remove(entrada.getKey(), ventana);
            }
        }
    }

    private static int entero(String clave, int predeterminado) {
        String valor = ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return numero > 0 ? numero : predeterminado;
        } catch (NumberFormatException exception) {
            return predeterminado;
        }
    }

    private static long segundos(String clave, long predeterminado) {
        String valor = ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        try {
            long numero = Long.parseLong(valor.trim());
            return numero > 0 ? numero : predeterminado;
        } catch (NumberFormatException exception) {
            return predeterminado;
        }
    }

    private static final class Ventana {
        private final long inicio;
        private int usos;
        private long ventanaMs;

        private Ventana(long inicio, int usos, long ventanaMs) {
            this.inicio = inicio;
            this.usos = usos;
            this.ventanaMs = ventanaMs;
        }
    }
}
