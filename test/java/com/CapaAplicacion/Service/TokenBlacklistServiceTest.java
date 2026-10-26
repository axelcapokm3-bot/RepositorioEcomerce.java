package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.IRevocacionToken;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private static final String TOKEN = "token.firmado";
    private static final long CUENTA = 9L;

    @Mock
    private IRevocacionToken repositorio;

    @Test
    @DisplayName("registra la revocacion con la expiracion real del token")
    void registraRevocacionVigente() {
        long expira = Instant.now().getEpochSecond() + 120;
        TokenBlacklistService servicio = new TokenBlacklistService(repositorio);

        servicio.revocar(TOKEN, CUENTA, expira);

        verify(repositorio).revocar(TOKEN, CUENTA, expira);
    }

    @Test
    @DisplayName("no persiste un token que ya expiro")
    void ignoraTokenExpirado() {
        TokenBlacklistService servicio = new TokenBlacklistService(repositorio);

        servicio.revocar(TOKEN, CUENTA, Instant.now().getEpochSecond() - 1);

        verify(repositorio, never()).revocar(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("consulta la lista negra antes de aceptar el token")
    void consultaEstadoRevocado() {
        when(repositorio.estaRevocado(TOKEN)).thenReturn(true);
        TokenBlacklistService servicio = new TokenBlacklistService(repositorio);

        assertTrue(servicio.estaRevocado(TOKEN));
        assertFalse(servicio.estaRevocado(""));
        assertFalse(servicio.estaRevocado(null));
    }

    @Test
    @DisplayName("rechaza datos incompletos al revocar")
    void validaDatosDeRevocacion() {
        TokenBlacklistService servicio = new TokenBlacklistService(repositorio);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.revocar(null, CUENTA, Instant.now().getEpochSecond() + 60));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.revocar(TOKEN, 0L, Instant.now().getEpochSecond() + 60));
    }
}
