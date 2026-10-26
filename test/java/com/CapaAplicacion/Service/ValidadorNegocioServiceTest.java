package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Excepciones.NegocioInactivoException;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidadorNegocioServiceTest {

    private static final long NEGOCIO = 4L;

    @Mock
    private INegocioRepository repositorio;

    @Test
    @DisplayName("exigirOperativo devuelve el negocio activo")
    void aceptaNegocioActivo() {
        Negocio negocio = new Negocio(NEGOCIO, "Activo", "activo", "a@mail.com",
                true, null, null);
        when(repositorio.buscarPorId(NEGOCIO)).thenReturn(Optional.of(negocio));

        ValidadorNegocioService validador = new ValidadorNegocioService(repositorio);

        assertSame(negocio, validador.exigirOperativo(NEGOCIO));
        assertDoesNotThrow(() -> validador.exigirOperativo(NEGOCIO));
    }

    @Test
    @DisplayName("exigirOperativo rechaza un negocio inactivo con excepcion de dominio")
    void rechazaNegocioInactivo() {
        when(repositorio.buscarPorId(NEGOCIO)).thenReturn(Optional.of(
                new Negocio(NEGOCIO, "Inactivo", "inactivo", "i@mail.com",
                        false, null, null)));
        ValidadorNegocioService validador = new ValidadorNegocioService(repositorio);

        assertThrows(NegocioInactivoException.class,
                () -> validador.exigirOperativo(NEGOCIO));
    }

    @Test
    @DisplayName("exigirOperativo rechaza un negocio inexistente")
    void rechazaNegocioInexistente() {
        when(repositorio.buscarPorId(NEGOCIO)).thenReturn(Optional.empty());
        ValidadorNegocioService validador = new ValidadorNegocioService(repositorio);

        assertThrows(NegocioInactivoException.class,
                () -> validador.exigirOperativo(NEGOCIO));
    }
}
