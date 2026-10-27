package com.CapaPresentacion.Utilidades;

import com.CapaAplicacion.Interfaces.RepositorioCacheTenant;
import java.util.HashMap;
import java.util.Map;

public final class CacheTenantEnMemoria implements RepositorioCacheTenant {

    private final Map<String, String> valores = new HashMap<>();

    @Override
    public void guardar(String subdominio, String idNegocio, long ttlSegundos) {
        valores.put(subdominio, idNegocio);
    }

    @Override
    public String obtener(String subdominio) {
        return valores.get(subdominio);
    }

    @Override
    public void invalidar(String subdominio) {
        valores.remove(subdominio);
    }
}
