package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface INegocioRepository {
    Optional<Negocio> buscarPorSubdominio(String subdominio);
    Optional<Negocio> buscarPorId(int id);
    Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(int idNegocio);
    Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave);
    void guardarOActualizarValorConfiguracion(int idNegocio, int idConfiguracion, String valor);
}
