package com.CapaAplicacion.Seguridad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaDominio.Entidades.RolUsuario;

import org.junit.jupiter.api.Test;

class ControlAccesoTest {
    private final Principal admin = new Principal(1, 1, 1L, RolUsuario.ADMIN);
    private final Principal cliente = new Principal(2, 5, 1L, RolUsuario.CLIENTE);
    private final Principal otroNegocio = new Principal(3, 6, 2L, RolUsuario.ADMIN);

    @Test
    void adminOperaSobreCualquierCliente() {
        assertTrue(ControlAcceso.puedeOperarCliente(admin, 1));
        assertTrue(ControlAcceso.puedeOperarCliente(admin, 999));
    }

    @Test
    void clienteSoloOperaSobreSusDatos() {
        assertTrue(ControlAcceso.puedeOperarCliente(cliente, 5));
        assertFalse(ControlAcceso.puedeOperarCliente(cliente, 6));
    }

    @Test
    void rechazaPrincipalNuloOIdInvalido() {
        assertFalse(ControlAcceso.puedeOperarCliente(null, 5));
        assertFalse(ControlAcceso.puedeOperarCliente(cliente, 0));
        assertFalse(ControlAcceso.esAdmin(null));
        assertFalse(ControlAcceso.esAdmin(cliente));
        assertTrue(ControlAcceso.esAdmin(admin));
    }

    @Test
    void operaSoloSobreSuNegocio() {
        assertTrue(ControlAcceso.puedeOperarNegocio(admin, 1L));
        assertFalse(ControlAcceso.puedeOperarNegocio(admin, 2L));
        assertTrue(ControlAcceso.puedeOperarNegocio(otroNegocio, 2L));
        assertFalse(ControlAcceso.puedeOperarNegocio(otroNegocio, 1L));
        assertFalse(ControlAcceso.puedeOperarNegocio(null, 1L));
    }
}
