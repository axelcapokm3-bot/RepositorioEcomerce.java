package com.CapaPresentacion.Utilidades;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.CapaAplicacion.Interfaces.RepositorioRateLimit;
import com.CapaInfraestructura.Configuracion.ConfigLoader;

public final class LimitadorTasa {
    private static final long VENTANA_PREDETERMINADA_SEGUNDOS = 60L;
    private static final long LIMPIEZA_PREDETERMINADA_SEGUNDOS = 60L;
    private static final int MAX_CLAVES_PREDETERMINADO = 10_000;
    private static final Map<String, Ventana> VENTANAS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> tiempoDeBloqueo = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ContadorIntentos> intentosPorIp = new ConcurrentHashMap<>();

    private static ScheduledExecutorService limpieza;
    private static RepositorioRateLimit repositorioRateLimit;

    private LimitadorTasa() {
    }

    public static synchronized void setRepositorioRateLimit(RepositorioRateLimit repo) {
        repositorioRateLimit = repo;
    }

    public static record Resultado(boolean excedido, long segundosParaReintentar) {
        public static Resultado permitido() {
            return new Resultado(false, 0L);
        }
    }

    public static Resultado evaluar(String clave, int maxPorVentana, long ventanaSegundos) {
        if (clave == null || clave.isBlank() || maxPorVentana <= 0) {
            return Resultado.permitido();
        }
        long ventana = Math.max(1L, ventanaSegundos);
        if (repositorioRateLimit != null) {
            try {
                long usos = repositorioRateLimit.incrementarYVerificar(clave, maxPorVentana, ventana);
                if (usos > maxPorVentana) {
                    return new Resultado(true, ventana);
                }
                return Resultado.permitido();
            } catch (RuntimeException e) {
                // Fail-Open: fallback a memoria local
            }
        }
        long ventanaMs = ventana * 1000L;
        long ahora = System.currentTimeMillis();
        Ventana ventanaObj = VENTANAS.compute(clave, (k, actual) -> {
            if (actual == null || ahora - actual.inicio >= ventanaMs) {
                return new Ventana(ahora, 1, ventanaMs);
            }
            actual.usos++;
            actual.ventanaMs = ventanaMs;
            return actual;
        });
        mantenerCapacidad(ahora);
        if (ventanaObj.usos > maxPorVentana) {
            long restanteMs = Math.max(1L, ventanaObj.inicio + ventanaObj.ventanaMs - ahora);
            long segundos = Math.max(1L, (restanteMs + 999L) / 1000L);
            return new Resultado(true, segundos);
        }
        return Resultado.permitido();
    }

    public static Resultado evaluarIntento(String clave, int maxIntentos,
                                           long ventanaSegundos, long bloqueoSegundos) {
        if (clave == null || clave.isBlank() || maxIntentos <= 0) {
            return Resultado.permitido();
        }
        long ventanaMs = Math.max(1L, ventanaSegundos) * 1000L;
        long bloqueoMs = Math.max(1L, bloqueoSegundos) * 1000L;
        long ahora = System.currentTimeMillis();

        Long bloqueadoHasta = tiempoDeBloqueo.get(clave);
        if (bloqueadoHasta != null) {
            if (ahora < bloqueadoHasta) {
                return new Resultado(true, segundosRestantes(bloqueadoHasta - ahora));
            }
            tiempoDeBloqueo.remove(clave);
            intentosPorIp.remove(clave);
        }

        ContadorIntentos contador = intentosPorIp.compute(clave, (k, actual) -> {
            if (actual == null || ahora - actual.inicio >= actual.ventanaMs) {
                return new ContadorIntentos(1, ahora, ventanaMs);
            }
            return new ContadorIntentos(actual.intentos + 1, actual.inicio, actual.ventanaMs);
        });

        if (contador.intentos > maxIntentos) {
            long hasta = tiempoDeBloqueo.merge(clave, ahora + bloqueoMs,
                    (actual, nuevo) -> actual >= nuevo ? actual : nuevo);
            mantenerCapacidadIntentos(ahora);
            return new Resultado(true, segundosRestantes(hasta - ahora));
        }
        mantenerCapacidadIntentos(ahora);
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
            int especifico = entero("rate.limit." + ambito + "." + sufijo, -1);
            if (especifico <= 0) {
                especifico = entero("rate-limit." + ambito + "." + sufijo, -1);
            }
            if (especifico > 0) {
                return especifico;
            }
        }
        int valor = entero("rate.limit." + ambito + ".limit", -1);
        if (valor <= 0) {
            valor = entero("rate.limit." + ambito + ".limite", -1);
        }
        if (valor <= 0) {
            valor = entero("rate-limit." + ambito + ".limite", -1);
        }
        if (valor <= 0 && "login".equalsIgnoreCase(ambito)) {
            valor = entero("rate.limit.login.limit", 5);
        }
        if (valor <= 0 && "escritura".equalsIgnoreCase(ambito)) {
            valor = entero("rate.limit.escritura.limit", 10);
        }
        if (valor <= 0 && "lectura".equalsIgnoreCase(ambito)) {
            valor = entero("rate.limit.lectura.limit", 200);
        }
        return valor > 0 ? valor : predeterminado;
    }

    public static long ventanaSegundos(String ambito) {
        long global = segundos("rate.limit.window", -1L);
        if (global <= 0) {
            global = segundos("rate-limit.ventana-segundos", VENTANA_PREDETERMINADA_SEGUNDOS);
        }
        if (ambito == null || ambito.isBlank()) {
            return global;
        }
        long especifica = segundos("rate.limit." + ambito + ".window", -1L);
        if (especifica <= 0) {
            especifica = segundos("rate.limit." + ambito + ".ventana-segundos", -1L);
        }
        if (especifica <= 0) {
            especifica = segundos("rate-limit." + ambito + ".ventana-segundos", -1L);
        }
        if (especifica <= 0 && "login".equalsIgnoreCase(ambito)) {
            especifica = segundos("rate.limit.login.window", 60L);
        }
        if (especifica <= 0 && "escritura".equalsIgnoreCase(ambito)) {
            especifica = segundos("rate.limit.escritura.window", 60L);
        }
        if (especifica <= 0 && "lectura".equalsIgnoreCase(ambito)) {
            especifica = segundos("rate.limit.lectura.window", 60L);
        }
        return especifica > 0 ? especifica : global;
    }

    public static long bloqueoSegundos(String ambito, long predeterminado) {
        if (ambito == null || ambito.isBlank()) {
            return predeterminado;
        }
        return segundos("rate-limit." + ambito + ".bloqueo-segundos", predeterminado);
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
        tiempoDeBloqueo.clear();
        intentosPorIp.clear();
    }

    static void limpiar() {
        VENTANAS.clear();
        tiempoDeBloqueo.clear();
        intentosPorIp.clear();
    }

    static int cantidadClaves() {
        return VENTANAS.size();
    }

    static int cantidadBloqueos() {
        return tiempoDeBloqueo.size();
    }

    static int cantidadIntentos() {
        return intentosPorIp.size();
    }

    static boolean estaBloqueada(String clave) {
        Long hasta = tiempoDeBloqueo.get(clave);
        return hasta != null && System.currentTimeMillis() < hasta;
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

    private static void mantenerCapacidadIntentos(long ahora) {
        int maximo = entero("rate-limit.max-claves", MAX_CLAVES_PREDETERMINADO);
        if (maximo <= 0 || intentosPorIp.size() <= maximo) {
            return;
        }
        limpiarExpiradas(ahora);
        while (intentosPorIp.size() > maximo) {
            String claveMasAntigua = null;
            long inicioMasAntiguo = Long.MAX_VALUE;
            for (Map.Entry<String, ContadorIntentos> entrada : intentosPorIp.entrySet()) {
                if (entrada.getValue().inicio < inicioMasAntiguo) {
                    inicioMasAntiguo = entrada.getValue().inicio;
                    claveMasAntigua = entrada.getKey();
                }
            }
            if (claveMasAntigua == null || intentosPorIp.remove(claveMasAntigua) == null) {
                return;
            }
            tiempoDeBloqueo.remove(claveMasAntigua);
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
        for (Map.Entry<String, Long> entrada : tiempoDeBloqueo.entrySet()) {
            if (ahora >= entrada.getValue()) {
                tiempoDeBloqueo.remove(entrada.getKey(), entrada.getValue());
                intentosPorIp.remove(entrada.getKey());
            }
        }
        for (Map.Entry<String, ContadorIntentos> entrada : intentosPorIp.entrySet()) {
            ContadorIntentos contador = entrada.getValue();
            if (ahora - contador.inicio >= contador.ventanaMs) {
                intentosPorIp.remove(entrada.getKey(), contador);
            }
        }
    }

    private static long segundosRestantes(long restanteMs) {
        return Math.max(1L, (Math.max(1L, restanteMs) + 999L) / 1000L);
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

    private record ContadorIntentos(int intentos, long inicio, long ventanaMs) {
    }
}
