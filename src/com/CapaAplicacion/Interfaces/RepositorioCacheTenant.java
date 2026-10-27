package com.CapaAplicacion.Interfaces;

public interface RepositorioCacheTenant {

    void guardar(String subdominio, String idNegocio, long ttlSegundos);

    String obtener(String subdominio);

    void invalidar(String subdominio);
}
