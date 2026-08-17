package com.CapaAplicacion.DTOsalida;

import java.math.BigDecimal;

public record CuentaResponseDTO (
    int id,
    String nombreCuenta,
    String correoElectronico,
    String rolUsuario,
    String token,
    BigDecimal saldo 

) {

}
    

