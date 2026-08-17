package com.CapaAplicacion.DTOsalida;

import java.time.LocalDate;

public record ClienteResponseDTO(
    int id,
    String dni,
    String cuit,
    String nombreCompleto, 
    String direccion,
    LocalDate fechaNacimiento,
    String localidad,
    boolean activo
) {}