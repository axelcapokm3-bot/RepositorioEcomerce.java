package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOsalida.ConfiguracionResponseDTO;
import com.CapaAplicacion.Interfaces.IInvalidadorCacheNegocio;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.INegocioService;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.ReglaNegocioException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NegocioService implements INegocioService {
    private final INegocioRepository negocioRepository;
    private final IInvalidadorCacheNegocio invalidadorCache;

    public NegocioService(INegocioRepository negocioRepository) {
        this(negocioRepository, null);
    }

    public NegocioService(INegocioRepository negocioRepository,
                          IInvalidadorCacheNegocio invalidadorCache) {
        if (negocioRepository == null) {
            throw new IllegalArgumentException("El repositorio de negocios es obligatorio.");
        }
        this.negocioRepository = negocioRepository;
        this.invalidadorCache = invalidadorCache == null
                ? new IInvalidadorCacheNegocio() {
                    @Override
                    public void invalidar(long idNegocio) {
                    }
                }
                : invalidadorCache;
    }

    @Override
    public Optional<Negocio> buscarNegocioPorSubdominio(String subdominio) {
        return negocioRepository.buscarPorSubdominio(subdominio);
    }

    @Override
    public List<ConfiguracionResponseDTO> listarConfiguraciones(String subdominio) {
        Negocio negocio = validarYObtenerNegocioActivo(subdominio);
        Map<ConfiguracionCatalogo, String> configsEfectivas = negocioRepository.listarConfiguracionesEfectivas(negocio.getId());
        
        List<ConfiguracionResponseDTO> listaDTO = new ArrayList<>();
        for (Map.Entry<ConfiguracionCatalogo, String> entry : configsEfectivas.entrySet()) {
            ConfiguracionCatalogo cc = entry.getKey();
            listaDTO.add(new ConfiguracionResponseDTO(
                cc.getClave(),
                cc.getTipo(),
                cc.getDescripcion(),
                entry.getValue(),
                cc.getCategoria()
            ));
        }
        return listaDTO;
    }

    @Override
    public void actualizarConfiguracion(String subdominio, String clave, String valor) {
        Negocio negocio = validarYObtenerNegocioActivo(subdominio);

        if (clave == null || clave.trim().isEmpty()) {
            throw new IllegalArgumentException("La clave de configuración no puede estar vacía.");
        }

        ConfiguracionCatalogo configCatalogo = negocioRepository.buscarConfiguracionPorClave(clave)
            .orElseThrow(() -> new RecursoNoEncontradoException("La clave de configuración '" + clave + "' no existe en el catálogo."));

        negocioRepository.guardarOActualizarValorConfiguracion(negocio.getId(), configCatalogo.getId(), valor);
        invalidadorCache.invalidar(negocio.getId());
    }

    @Override
    public void darDeBaja(long idNegocio) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio a dar de baja no es válido.");
        }
        Negocio negocio = negocioRepository.buscarPorId(idNegocio)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el negocio solicitado."));
        if (!negocio.estaOperativo()) {
            throw new ReglaNegocioException("El negocio ya se encuentra dado de baja.");
        }
        negocio.darDeBaja(LocalDateTime.now());
        negocioRepository.actualizarEstado(negocio);
        invalidadorCache.invalidar(idNegocio);
    }

    private Negocio validarYObtenerNegocioActivo(String subdominio) {
        if (subdominio == null || subdominio.trim().isEmpty()) {
            throw new IllegalArgumentException("El subdominio es obligatorio.");
        }
        Negocio negocio = negocioRepository.buscarPorSubdominio(subdominio)
            .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró un negocio con el subdominio: " + subdominio));

        if (!negocio.estaOperativo()) {
            throw new ReglaNegocioException("El negocio con subdominio '" + subdominio + "' se encuentra inactivo.");
        }
        return negocio;
    }
}
