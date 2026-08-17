package com.CapaDominio.Entidades;

import java.time.LocalDate;

public class Cliente {
    private int id;
    private String dni;
    private String cuit;
    private String nombre;
    private String apellido;
    private String direccion;
    private LocalDate fechaNacimiento;
    private String localidad;
    private boolean activo;
    private String motivoBaja;

    public Cliente(int id, String dni, String nombre, String apellido, String direccion, LocalDate fechaNacimiento, String localidad) {

        if (id < 0) throw new IllegalArgumentException("El ID del cliente no puede ser negativo.");
        if (dni == null || dni.trim().isEmpty()) throw new IllegalArgumentException("El DNI es obligatorio.");
        if (nombre == null || nombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre es obligatorio.");
        if (apellido == null || apellido.trim().isEmpty()) throw new IllegalArgumentException("El apellido es obligatorio.");
        if (fechaNacimiento == null || fechaNacimiento.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no es válida.");
        }

        this.id = id;
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.direccion = direccion;
        this.fechaNacimiento = fechaNacimiento;
        this.localidad = localidad;
        this.cuit = "";
        this.activo = true;
    }

    public boolean esMayorDeEdad() {
    if (this.fechaNacimiento == null) {
        return false;
    }

    int anioNacimiento = this.fechaNacimiento.getYear();
    int anioLimite = LocalDate.now().getYear() - 18; 

   
    return anioNacimiento <= anioLimite;
}

public void darDeBaja(String motivo) {
   
    if (!this.activo) {
        throw new IllegalStateException("El cliente ID " + this.id + " ya se encuentra dado de baja.");
    }


    if (motivo == null || motivo.trim().isEmpty()) {
        throw new IllegalArgumentException("Es obligatorio especificar el motivo de la baja del cliente.");
    }

    this.activo = false;
    this.motivoBaja = motivo;
}

public void reactivar() {

    if (this.activo) {
        throw new IllegalStateException("El cliente ID " + this.id + " ya está activo en el sistema.");
    }

    this.activo = true;
    this.motivoBaja = ""; 

}

public void asignarElCuit(String cuit ) { 
    if(cuit == null || cuit.trim().isEmpty())  {
    throw new IllegalArgumentException("El CUIT no puede estar vacío.");
    }
    String cuitLimpio = cuit.trim()  ; 
    if(cuitLimpio.length() != 11){
        throw new IllegalArgumentException("El CUIT debe tener exactamente 11 dígitos.");
    }

    for(int i =  0 ;  i < cuitLimpio.length() ; i ++ )
 { 
        char c = cuitLimpio.charAt(i) ;

        if(!Character.isDigit(c)) { 
            throw new IllegalArgumentException("El CUIT debe contener únicamente dígitos numéricos.");
        }
        }
            this.cuit = cuitLimpio;
 }    


    public int getId() { return id; }

    public void setId(int id) {
        if (this.id != 0) {
            throw new IllegalStateException("El ID ya ha sido asignado y no puede modificarse.");
        }
        this.id = id;
    }

    public String getDni() { return dni; }
    
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }

    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
