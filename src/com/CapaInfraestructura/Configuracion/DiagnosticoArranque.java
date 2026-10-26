package com.CapaInfraestructura.Configuracion;

/** Emite diagnósticos de arranque en formato estructurado y saneado. */
public final class DiagnosticoArranque {

    public static final int CODIGO_SALIDA_FALLO = 1;

    private DiagnosticoArranque() {
    }

    public static String linea(String evento, String componente, Throwable error) {
        return "event=" + valor(evento)
                + " component=" + valor(componente)
                + " error=" + SanitizadorLog.error(error);
    }

    public static void registrar(String evento, String componente, Throwable error) {
        System.err.println(linea(evento, componente, error));
    }

    private static String valor(String texto) {
        String saneado = SanitizadorLog.linea(texto);
        return saneado == null || saneado.isBlank() ? "desconocido" : saneado;
    }
}
