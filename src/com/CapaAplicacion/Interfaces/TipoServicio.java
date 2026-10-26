package com.CapaAplicacion.Interfaces;


public enum TipoServicio {

    VENTA("venta"),
    PRODUCTO("producto"),
    CLIENTE("cliente"),
    AUDITORIA("auditoria"),
    NOTIFICACIONES("notificacion"),
    AUTENTICACION("autenticacion");

    private final String prefijoHilo;

    TipoServicio(String prefijoHilo) {
        this.prefijoHilo = prefijoHilo;
    }

    public String getPrefijoHilo() {
        return prefijoHilo;
    }
}
