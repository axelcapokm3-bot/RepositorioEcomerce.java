package com.CapaAplicacion.Interfaces;

public interface IServicioContrasena {
    String generarHash(String contraseniaPlana);

    boolean verificar(String contraseniaPlana, String hashAlmacenado);
}
