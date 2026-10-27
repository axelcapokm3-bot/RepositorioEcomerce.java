package com.CapaPresentacion.Controllers;

import static org.mockito.Mockito.mock;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaPresentacion.Utilidades.CacheTenantEnMemoria;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;

final class ResolutorNegocioPrueba {

    private ResolutorNegocioPrueba() {
    }

    static ResolutorNegocio crear() {
        return new ResolutorNegocio(
                mock(INegocioRepository.class), new CacheTenantEnMemoria());
    }
}
