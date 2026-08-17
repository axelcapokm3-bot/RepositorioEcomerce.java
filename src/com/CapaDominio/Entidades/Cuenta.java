package com.CapaDominio.Entidades;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Cuenta {
    private int id;
    private String nombreCuenta;
    private String correoElectronico;
    private String contrasenia;
    private String rolUsuario;
    private String token;
    private int idCliente;
    private BigDecimal saldo;

    private Set<String> roles; 

    public Cuenta() {
        this.token = "";
        this.saldo = BigDecimal.ZERO;
        this.roles = new HashSet<>(); 
    }

    public Cuenta(int id, String nombreCuenta, String correoElectronico, String contrasenia, String rolUsuario, int idCliente) {
        this(id, nombreCuenta, correoElectronico, contrasenia, rolUsuario, idCliente, BigDecimal.ZERO);
    }

    public Cuenta(int id, String nombreCuenta, String correoElectronico, String contrasenia, String rolUsuario, int idCliente, BigDecimal saldo) {
        if (id < 0) {
            throw new IllegalArgumentException("El ID de la cuenta no puede ser negativo.");
        }
        if (nombreCuenta == null || nombreCuenta.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de cuenta es obligatorio.");
        }
        if (correoElectronico == null || !correoElectronico.contains("@")) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }
        if (contrasenia == null || contrasenia.trim().length() < 6) { // Corrección: valida longitud real
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (rolUsuario == null || rolUsuario.trim().isEmpty()) {
            throw new IllegalArgumentException("El rol de usuario es obligatorio.");
        }
        if (idCliente <= 0) {
            throw new IllegalArgumentException("La cuenta debe estar asociada a un cliente válido.");
        }
        if (saldo == null || saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo no puede ser nulo ni negativo.");
        }

        this.id = id;
        this.nombreCuenta = nombreCuenta;
        this.correoElectronico = correoElectronico;
        this.contrasenia = contrasenia;
        this.rolUsuario = rolUsuario;
        this.idCliente = idCliente;
        this.saldo = saldo;
        this.token = "";
        
      
        this.roles = new HashSet<>();
        this.agregarRol(rolUsuario);
    }



    public void agregarRol(String rol) { 
        if (rol != null && !rol.trim().isEmpty()) { 
            this.roles.add(rol.trim().toUpperCase());
        }
    }

    public void removerRol(String rol) {
        if (rol != null && !rol.trim().isEmpty()) { 
            this.roles.remove(rol.trim().toUpperCase());
        }
    }

    public boolean tieneRol(String rol) {
        if (rol == null) return false;
        return this.roles.contains(rol.trim().toUpperCase());
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(this.roles);
    }



    public void acreditar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a depositar debe ser mayor a cero.");
        }
        this.saldo = this.saldo.add(monto);
    }

    public void debitar(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a retirar debe ser mayor a cero.");
        }
        if (this.saldo.compareTo(monto) < 0) {
            throw new IllegalStateException("Saldo insuficiente para realizar la extracción.");
        }
        this.saldo = this.saldo.subtract(monto);
    }

    public void transferirA(Cuenta cuentaDestino, BigDecimal monto) {
        if (cuentaDestino == null) {
            throw new IllegalArgumentException("La cuenta destino no existe.");
        }
        if (cuentaDestino.getId() == this.id) {
            throw new IllegalArgumentException("No se puede realizar una transferencia a la misma cuenta.");
        }
        this.debitar(monto);
        cuentaDestino.acreditar(monto);
    }


    public int getId() { return id; }

    public void setId(int id) {
        if (this.id != 0) {
            throw new IllegalStateException("El ID ya ha sido asignado y no puede modificarse.");
        }
        this.id = id;
    }

    public String getNombreCuenta() { return nombreCuenta; }
    public void setNombreCuenta(String nombreCuenta) { this.nombreCuenta = nombreCuenta; }
    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }
    public String getContrasenia() { return contrasenia; }
    public void setContrasenia(String contrasenia) { this.contrasenia = contrasenia; }
    public String getRolUsuario() { return rolUsuario; }
    public void setRolUsuario(String rolUsuario) { this.rolUsuario = rolUsuario; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }
    public BigDecimal getSaldo() { return saldo; }
}