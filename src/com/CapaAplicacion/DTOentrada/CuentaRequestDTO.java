package com.CapaAplicacion.DTOentrada;
import java.math.BigDecimal;

public record CuentaRequestDTO(
    String nombreCuenta,
    String correoElectronico,
    String contraseniaPlana,
    int idCliente,
    String rolUsuario ,
    BigDecimal saldo 
) 
   {
    public CuentaRequestDTO {
        
        
        if (nombreCuenta == null || nombreCuenta.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de cuenta es obligatorio.");
        }
        if (nombreCuenta.contains(" ")) {
            throw new IllegalArgumentException("El nombre de cuenta no puede contener espacios.");
        }

     
        if (correoElectronico == null || !correoElectronico.contains("@") || !correoElectronico.endsWith(".com")) {
            throw new IllegalArgumentException("El correo electrónico debe ser válido.");
        }

     
        if (contraseniaPlana == null || contraseniaPlana.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        
        
        boolean tieneMayuscula = false;
        boolean tieneMinuscula = false;
        boolean tieneNumero = false;

        for (char c : contraseniaPlana.toCharArray()) {
            if (Character.isUpperCase(c)) {
                tieneMayuscula = true; 
            }
            if (Character.isLowerCase(c)) {
                tieneMinuscula = true; 
            }
            if (Character.isDigit(c)) {
                tieneNumero = true; 
            } 
        }

        if (!tieneMayuscula || !tieneMinuscula || !tieneNumero) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una mayúscula, una minúscula y un número.");
        }
        
        if (idCliente <= 0) {
            throw new IllegalArgumentException("La cuenta debe estar vinculada a un cliente válido.");
        }
        if (saldo == null) {
            throw new IllegalArgumentException("El saldo inicial es obligatorio.");
        }


        if(saldo.compareTo(BigDecimal.ZERO) < 0) { 
              throw new IllegalArgumentException("No se admiten valores negativos "); 
        }
    } 
   }