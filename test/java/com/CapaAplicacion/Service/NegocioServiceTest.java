package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.IInvalidadorCacheNegocio;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NegocioServiceTest {

    private static final long NEGOCIO = 3L;

    @Mock
    private INegocioRepository repositorio;

    @Mock
    private IInvalidadorCacheNegocio invalidadorCache;

    private NegocioService servicio;

    @BeforeEach
    void setUp() {
        servicio = new NegocioService(repositorio, invalidadorCache);
    }

    @Test
    @DisplayName("darDeBaja persiste el estado e invalida el cache del tenant")
    void darDeBajaInvalidaCache() {
        Negocio negocio = new Negocio(NEGOCIO, "Uno", "uno", "uno@mail.com",
                true, null, null);
        when(repositorio.buscarPorId(NEGOCIO)).thenReturn(Optional.of(negocio));

        servicio.darDeBaja(NEGOCIO);

        ArgumentCaptor<Negocio> capturado = ArgumentCaptor.forClass(Negocio.class);
        verify(repositorio).actualizarEstado(capturado.capture());
        assertFalse(capturado.getValue().estaOperativo());
        assertNotNull(capturado.getValue().getFechaBaja());
        verify(invalidadorCache).invalidar(NEGOCIO);
    }

    @Test
    @DisplayName("actualizarConfiguracion invalida el cache desde el servicio")
    void actualizarConfiguracionInvalidaCache() {
        Negocio negocio = new Negocio(NEGOCIO, "Uno", "uno", "uno@mail.com",
                true, null, null);
        ConfiguracionCatalogo configuracion =
                new ConfiguracionCatalogo(8L, "clave", "STRING", "demo", "x", "general");
        when(repositorio.buscarPorSubdominio("uno")).thenReturn(Optional.of(negocio));
        when(repositorio.buscarConfiguracionPorClave("clave"))
                .thenReturn(Optional.of(configuracion));

        servicio.actualizarConfiguracion("uno", "clave", "nuevo");

        verify(repositorio).guardarOActualizarValorConfiguracion(NEGOCIO, 8L, "nuevo");
        verify(invalidadorCache).invalidar(NEGOCIO);
    }
}
