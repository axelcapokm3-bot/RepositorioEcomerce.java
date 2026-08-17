package com.CapaAplicacion.DTOentrada;
import java.time.Period;
import java.time.LocalDate;

public record ClienteRequestDTO(
    String dni,
    String cuit,
    String nombre,
    String apellido,
    String direccion,
    LocalDate fechaNacimiento,
    String localidad
    

)  { 
    public ClienteRequestDTO {

        if (dni == null || dni.trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI es obligatorio.");
        }
        String dniLimpio = dni.trim();
        if (dniLimpio.length() < 7 || dniLimpio.length() > 8) {
            throw new IllegalArgumentException("El DNI debe contener entre 7 y 8 caracteres.");
        }
        for (char c : dniLimpio.toCharArray()) {
            if (!Character.isDigit(c)) {
                throw new IllegalArgumentException("El DNI debe contener únicamente números.");
            }
        }

        if (cuit != null && !cuit.trim().isEmpty()) {
            String cuitLimpio = cuit.trim();
            if (cuitLimpio.length() != 11) {
                throw new IllegalArgumentException("El CUIT debe contener exactamente 11 dígitos.");
            }
            for (char c : cuitLimpio.toCharArray()) {
                if (!Character.isDigit(c)) {
                    throw new IllegalArgumentException("El CUIT debe contener únicamente números.");
                }
            }
        }

      
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        for (char c : nombre.toCharArray()) {
        
            if (!Character.isLetter(c) && c != ' ') {
                throw new IllegalArgumentException("El nombre solo puede contener letras y espacios.");
            }
        }

    
        if (apellido == null || apellido.trim().isEmpty()) {
            throw new IllegalArgumentException("El apellido es obligatorio.");
        }
        for (char c : apellido.toCharArray()) {
            if (!Character.isLetter(c) && c != ' ') {
                throw new IllegalArgumentException("El apellido solo puede contener letras y espacios.");
            }
        }

        if (direccion == null || direccion.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección es obligatoria.");
        }
        if (localidad == null || localidad.trim().isEmpty()) {
            throw new IllegalArgumentException("La localidad es obligatoria.");
        }

     
        if (fechaNacimiento == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        if (fechaNacimiento.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser una fecha futura.");
        }
        
     
        int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
        if (edad < 18) {
            throw new IllegalArgumentException("El cliente debe ser mayor de 18 años para registrarse.");
        }
    }
}
