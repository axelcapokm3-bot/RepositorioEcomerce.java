package com.CapaAplicacion.Interfaces;

public interface ITransaccion {

    void ejecutarTransaccion(OperacionTransaccional operacion);

    default void EjecutarTransaccion(Runnable runnable) {
        ejecutarTransaccion(connection -> runnable.run());
    }
}
