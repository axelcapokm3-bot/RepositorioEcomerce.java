package com.CapaAplicacion.Interfaces;

import java.sql.Connection;
import java.time.LocalDateTime;

/** Persistencia del token de bootstrap administrativo, de un solo uso. */
public interface IRepositorioTokenBootstrap {

    void sembrarSiAusente(String huella, LocalDateTime expiraEn);

    boolean reclamar(Connection conexion, String huella);
}
