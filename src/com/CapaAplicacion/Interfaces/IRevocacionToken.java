package com.CapaAplicacion.Interfaces;

public interface IRevocacionToken {

    void revocar(String token, long idCuenta, long expiraEnEpochSegundos);

    boolean estaRevocado(String token);
}
