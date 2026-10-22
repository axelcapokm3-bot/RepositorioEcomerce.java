package com.CapaDominio.Entidades;

/** Identidad global de autenticación. Sin negocio ni rol propios: el acceso por
 * negocio vive en VinculoCuentaNegocio. */
public class Cuenta {
    private long id;
    private String nombreCuenta;
    private String correoElectronico;
    private String hashContrasenia;
    private EstadoCuenta estado;

    public Cuenta(long id, String nombreCuenta, String correoElectronico,
                  String hashContrasenia) {
        setId(id);
        setNombreCuenta(nombreCuenta);
        setCorreoElectronico(correoElectronico);
        setHashContrasenia(hashContrasenia);
        this.estado = EstadoCuenta.ACTIVA;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("El ID de cuenta no puede ser negativo.");
        }
        this.id = id;
    }

    public String getNombreCuenta() {
        return nombreCuenta;
    }

    public void setNombreCuenta(String nombreCuenta) {
        if (nombreCuenta == null || nombreCuenta.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de cuenta es obligatorio.");
        }
        String limpio = nombreCuenta.trim();
        if (limpio.length() > 100) {
            throw new IllegalArgumentException("El nombre de cuenta no puede superar 100 caracteres.");
        }
        for (int i = 0; i < limpio.length(); i++) {
            if (Character.isWhitespace(limpio.charAt(i))) {
                throw new IllegalArgumentException("El nombre de cuenta no puede contener espacios.");
            }
        }
        this.nombreCuenta = limpio;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public void setCorreoElectronico(String correoElectronico) {
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        String limpio = correoElectronico.trim().toLowerCase();
        if (limpio.length() > 150) {
            throw new IllegalArgumentException("El correo electrónico no puede superar 150 caracteres.");
        }
        int arroba = limpio.indexOf('@');
        if (arroba <= 0 || arroba != limpio.lastIndexOf('@') || arroba == limpio.length() - 1) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        String dominio = limpio.substring(arroba + 1);
        if (dominio.indexOf('.') < 0) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        this.correoElectronico = limpio;
    }

    public String getHashContrasenia() {
        return hashContrasenia;
    }

    public void setHashContrasenia(String hashContrasenia) {
        if (hashContrasenia == null || hashContrasenia.trim().isEmpty()) {
            throw new IllegalArgumentException("El hash de contraseña es obligatorio.");
        }
        this.hashContrasenia = hashContrasenia;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuenta estado) {
        if (estado == null) {
            throw new IllegalArgumentException("El estado de la cuenta es obligatorio.");
        }
        this.estado = estado;
    }

    public boolean estaActiva() {
        return estado == EstadoCuenta.ACTIVA;
    }
}
