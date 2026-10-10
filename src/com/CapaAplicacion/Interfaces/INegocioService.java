package com.CapaAplicacion.Interfaces;

import com.CapaAplicacion.DTOsalida.ConfiguracionResponseDTO;
import com.CapaDominio.Entidades.Negocio;

import java.util.List;
import java.util.Optional;

public interface INegocioService {
    Optional<Negocio> buscarNegocioPorSubdominio(String subdominio);
    List<ConfiguracionResponseDTO> listarConfiguraciones(String subdominio);
    void actualizarConfiguracion(String subdominio, String clave, String valor);
}
