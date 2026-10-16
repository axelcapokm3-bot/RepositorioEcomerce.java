package com.CapaPresentacion.Utilidades;

import java.util.HashMap;
import java.util.Map;

public final class LimitadorTasa {
    private static final long VENTANA_MILIS = 60L * 1000L;
    private static final Map<String, Ventana> VENTANAS = new HashMap<>();

    private LimitadorTasa() {
    }

    public static boolean excede(String clave, int maxPorMinuto) {
        if (clave == null || clave.isEmpty() || maxPorMinuto <= 0) {
            return false;
        }
        long ahora = System.currentTimeMillis();
        synchronized (VENTANAS) {
            Ventana ventana = VENTANAS.get(clave);
            if (ventana == null || ahora - ventana.inicio >= VENTANA_MILIS) {
                VENTANAS.put(clave, new Ventana(ahora, 1));
                return false;
            }
            ventana.usos++;
            return ventana.usos > maxPorMinuto;
        }
    }

    static void limpiar() {
        synchronized (VENTANAS) {
            VENTANAS.clear();
        }
    }

    private static final class Ventana {
        private final long inicio;
        private int usos;

        private Ventana(long inicio, int usos) {
            this.inicio = inicio;
            this.usos = usos;
        }
    }
}
