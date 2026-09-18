package com.CapaAplicacion.Interfaces;


public enum TipoServicio {

    VENTA(
        "venta",   // prefijo del nombre del hilo
        5,                // hilos core (siempre vivos)
        10,               // hilos max (bajo carga alta) → 50% de Hikari
        100               // capacidad de la cola
    ),
    PRODUCTO(
        "producto",
        2,
        5,                // 25% de Hikari
        50
    ),
    CLIENTE(
        "cliente",
        2,
        2,                // 10% de Hikari
        30
    ),
    AUDITORIA(
        "auditoria",
        1,
        2,                // 10% de Hikari
        20
    ),
    NOTIFICACIONES(
        "notificacion",
        1,
        1,
        50
    );

    private final String prefijoHilo;
    private final int tamanoCore;
    private final int tamanoMax;
    private final int capacidadCola;

    TipoServicio(String prefijoHilo, int tamanoCore, int tamanoMax, int capacidadCola) {
        this.prefijoHilo = prefijoHilo;
        this.tamanoCore = tamanoCore;
        this.tamanoMax = tamanoMax;
        this.capacidadCola = capacidadCola;
    }

    public String getPrefijoHilo()    { return prefijoHilo; }
    public int getTamanoCore()        { return tamanoCore; }
    public int getTamanoMax()         { return tamanoMax; }
    public int getCapacidadCola()     { return capacidadCola; }
}
