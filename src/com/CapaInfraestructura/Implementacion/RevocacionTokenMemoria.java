package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IRevocacionToken;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RevocacionTokenMemoria implements IRevocacionToken {

    private final ConcurrentMap<String, Long> revocados = new ConcurrentHashMap<>();

    @Override
    public void revocar(String token, long idCuenta, long expiraEnEpochSegundos) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (expiraEnEpochSegundos <= Instant.now().getEpochSecond()) {
            return;
        }
        revocados.put(HuellaToken.calcular(token), expiraEnEpochSegundos);
    }

    @Override
    public boolean estaRevocado(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String huella = HuellaToken.calcular(token);
        Long expiracion = revocados.get(huella);
        if (expiracion == null) {
            return false;
        }
        if (expiracion <= Instant.now().getEpochSecond()) {
            revocados.remove(huella, expiracion);
            return false;
        }
        return true;
    }
}
