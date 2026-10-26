package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.IRevocacionToken;

import java.time.Instant;

/**
 * Lista negra explícita de tokens de acceso.
 *
 * <p>La implementación persistente ({@code RevocacionTokenMySql}) guarda la huella
 * SHA-256 del token, nunca el valor en claro. La expiración se recibe en epoch
 * segundos y debe ser la expiración real del JWT, de modo que el registro viva
 * exactamente lo que le queda al token.</p>
 */
public class TokenBlacklistService {

    private final IRevocacionToken repositorioRevocaciones;

    public TokenBlacklistService(IRevocacionToken repositorioRevocaciones) {
        if (repositorioRevocaciones == null) {
            throw new IllegalArgumentException("El repositorio de revocaciones es obligatorio.");
        }
        this.repositorioRevocaciones = repositorioRevocaciones;
    }

    public void revocar(String token, long idCuenta, long expiraEnEpochSegundos) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("El token a revocar es obligatorio.");
        }
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (expiraEnEpochSegundos <= Instant.now().getEpochSecond()) {
            return;
        }
        repositorioRevocaciones.revocar(token, idCuenta, expiraEnEpochSegundos);
    }

    public boolean estaRevocado(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return repositorioRevocaciones.estaRevocado(token);
    }
}
