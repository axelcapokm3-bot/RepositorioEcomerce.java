package com.CapaAplicacion.Interfaces;

public interface RepositorioRateLimit {
    long incrementarYVerificar(String clave, long limite, long ventanaSegundos);
}
