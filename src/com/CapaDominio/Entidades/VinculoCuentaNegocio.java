package com.CapaDominio.Entidades;

/** Vínculo entre una identidad global (cuenta), un negocio y su perfil de cliente. */
public class VinculoCuentaNegocio {
    private int id;
    private int idCuenta;
    private long idNegocio;
    private int idCliente;
    private RolUsuario rol;
    private EstadoCuenta estado;

    public VinculoCuentaNegocio(int id, int idCuenta, long idNegocio, int idCliente, RolUsuario rol) {
        setId(id);
        setIdCuenta(idCuenta);
        setIdNegocio(idNegocio);
        setIdCliente(idCliente);
        setRol(rol);
        this.estado = EstadoCuenta.ACTIVA;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("El ID del vínculo no puede ser negativo.");
        }
        this.id = id;
    }

    public int getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(int idCuenta) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El vínculo debe referenciar una cuenta válida.");
        }
        this.idCuenta = idCuenta;
    }

    public long getIdNegocio() {
        return idNegocio;
    }

    public void setIdNegocio(long idNegocio) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El vínculo debe referenciar un negocio válido.");
        }
        this.idNegocio = idNegocio;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El vínculo debe referenciar un perfil de cliente válido.");
        }
        this.idCliente = idCliente;
    }

    public RolUsuario getRol() {
        return rol;
    }

    public void setRol(RolUsuario rol) {
        if (rol == null) {
            throw new IllegalArgumentException("El rol del vínculo es obligatorio.");
        }
        this.rol = rol;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuenta estado) {
        if (estado == null) {
            throw new IllegalArgumentException("El estado del vínculo es obligatorio.");
        }
        this.estado = estado;
    }

    public boolean estaActivo() {
        return estado == EstadoCuenta.ACTIVA;
    }

    public boolean esAdmin() {
        return rol == RolUsuario.ADMIN;
    }
}
