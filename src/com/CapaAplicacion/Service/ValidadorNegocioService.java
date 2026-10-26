package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Excepciones.NegocioInactivoException;

import java.util.Optional;

/**
 * Punto único de validación de negocio activo para compras, carrito y registro.
 * La CapaPresentacion no debe repetir esta regla.
 */
public class ValidadorNegocioService {

    private final INegocioRepository negocioRepository;

    public ValidadorNegocioService(INegocioRepository negocioRepository) {
        if (negocioRepository == null) {
            throw new IllegalArgumentException("El repositorio de negocios es obligatorio.");
        }
        this.negocioRepository = negocioRepository;
    }

    public Negocio exigirOperativo(long idNegocio) {
        if (idNegocio <= 0) {
            throw new NegocioInactivoException("El negocio solicitado no es válido.");
        }
        Optional<Negocio> negocio = negocioRepository.buscarPorId(idNegocio);
        if (negocio.isEmpty() || !negocio.get().estaOperativo()) {
            throw new NegocioInactivoException(
                    "El negocio se encuentra inactivo o dado de baja.");
        }
        return negocio.get();
    }

    public boolean estaOperativo(long idNegocio) {
        if (idNegocio <= 0) {
            return false;
        }
        Optional<Negocio> negocio = negocioRepository.buscarPorId(idNegocio);
        return negocio.isPresent() && negocio.get().estaOperativo();
    }
}
