package com.CapaAplicacion.Interfaces;

public interface AdquiridorLock {
    boolean intentarAdquirir(String clave, long ttlSegundos);
    void liberar(String clave);
}
