package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.RolUsuario;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProveedorTokenAccesoTest {
    private static final byte[] SECRETO = "secreto-de-prueba-de-al-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8);

    @Test
    void emiteYValidaToken() {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600);
        String token = proveedor.emitir(7, 42, 3L, "CLIENTE");
        Optional<Principal> principal = proveedor.validar(token);
        assertTrue(principal.isPresent());
        assertEquals(7, principal.get().idCuenta());
        assertEquals(42, principal.get().idCliente());
        assertEquals(3L, principal.get().idNegocio());
        assertEquals(RolUsuario.CLIENTE, principal.get().rol());
    }

    @Test
    void rechazaTokenManipulado() {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600);
        String token = proveedor.emitir(7, 42, 3L, "ADMIN");
        String manipulado = "X" + token.substring(1);
        assertTrue(proveedor.validar(manipulado).isEmpty());
        assertTrue(proveedor.validar("sin-formato").isEmpty());
        assertTrue(proveedor.validar(null).isEmpty());
    }

    @Test
    void rechazaTokenExpirado() throws InterruptedException {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 1);
        String token = proveedor.emitir(1, 1, 1L, "CLIENTE");
        Thread.sleep(1100);
        assertTrue(proveedor.validar(token).isEmpty());
    }

    @Test
    void rechazaTokenDeOtroSecreto() {
        ProveedorTokenAcceso emisor = new ProveedorTokenAcceso(SECRETO, 3600);
        byte[] otro = "otro-secreto-distinto-de-32-bytes-como-minimo!!".getBytes(StandardCharsets.UTF_8);
        ProveedorTokenAcceso validador = new ProveedorTokenAcceso(otro, 3600);
        String token = emisor.emitir(1, 1, 1L, "CLIENTE");
        assertTrue(validador.validar(token).isEmpty());
    }
}
