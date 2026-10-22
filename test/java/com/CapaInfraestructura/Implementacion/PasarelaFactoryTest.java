package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Excepciones.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasarelaFactoryTest {

    @BeforeEach
    void inicializar() {
        PasarelaFactory.inicializar(mock(INegocioRepository.class));
    }

    @Test
    void rechazaTarjetaDeCreditoSinPasarelaConfigurada() {
        assertThrows(ReglaNegocioException.class,
                () -> PasarelaFactory.obtener(1L, TipoDePago.TARJETA_CREDITO));
    }
}
