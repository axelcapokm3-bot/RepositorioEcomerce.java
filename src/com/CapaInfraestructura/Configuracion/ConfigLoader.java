package com.CapaInfraestructura.Configuracion;

public final class ConfigLoader {
    private static final ResolutorConfiguracion RESOLUTOR = ResolutorConfiguracion.getInstancia();

    private ConfigLoader() {
    }

    public static String getProperty(String key) {
        return RESOLUTOR.obtener(key);
    }

    public static String getSecretProperty(String key) {
        return RESOLUTOR.obtenerSecreto(key);
    }

    public static void validarArranque() {
        RESOLUTOR.validarArranque();
    }

    static String obtenerNombreVariableEntorno(String key) {
        return ResolutorConfiguracion.obtenerNombreVariableEntorno(key);
    }
}
