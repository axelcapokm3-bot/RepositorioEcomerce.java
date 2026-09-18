package com.CapaAplicacion.Interfaces;

import java.sql.Connection;

@FunctionalInterface
public interface OperacionTransaccional {
    void ejecutar(Connection connection) throws Exception;
}
