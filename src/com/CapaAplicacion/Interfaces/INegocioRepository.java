package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface INegocioRepository {
    Optional<Negocio> buscarPorSubdominio(String subdominio);
    Optional<Negocio> buscarPorId(long id);
    List<Negocio> listarActivos();
    Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(long idNegocio);
    Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave);
    void guardarOActualizarValorConfiguracion(long idNegocio, long idConfiguracion, String valor);
}
