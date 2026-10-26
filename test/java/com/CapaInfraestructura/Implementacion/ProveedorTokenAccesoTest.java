package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.Interfaces.IRevocacionToken;
import com.CapaAplicacion.Interfaces.IValidadorSesion;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.RolUsuario;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    void revocaTokenDeInmediato() {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600);
        String token = proveedor.emitir(7, 42, 3L, "CLIENTE");

        assertTrue(proveedor.validar(token).isPresent());
        proveedor.revocar(token);

        assertTrue(proveedor.validar(token).isEmpty());
    }

    @Test
    void revocaConElTiempoRestanteDeVidaDelToken() {
        RegistroRevocaciones registro = new RegistroRevocaciones();
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600,
                new ValidadorSesionFalso(true), registro);
        long antes = Instant.now().getEpochSecond();

        String token = proveedor.emitir(7, 42, 3L, "CLIENTE");
        proveedor.revocar(token);

        assertEquals(1, registro.revocaciones);
        assertTrue(registro.expiracion >= antes + 3599);
        assertTrue(registro.expiracion <= Instant.now().getEpochSecond() + 3600);
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

    @Test
    void rechazaTokenSiLaSesionYaNoEstaVigente() {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600,
                new ValidadorSesionFalso(false));

        String token = proveedor.emitir(7, 42, 3L, "CLIENTE");

        assertTrue(proveedor.validar(token).isEmpty());
    }

    @Test
    void validaLaSesionConLosDatosDelToken() {
        ValidadorSesionFalso validador = new ValidadorSesionFalso(true);
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600, validador);

        String token = proveedor.emitir(7, 42, 3L, "ADMIN");
        Optional<Principal> principal = proveedor.validar(token);

        assertTrue(principal.isPresent());
        assertEquals(7, validador.idCuenta);
        assertEquals(42, validador.idCliente);
        assertEquals(3L, validador.idNegocio);
        assertEquals(RolUsuario.ADMIN, validador.rol);
    }

    @Test
    void fallaCerradoSiNoSePuedeVerificarLaSesion() {
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso(SECRETO, 3600,
                new IValidadorSesion() {
                    @Override
                    public boolean estaVigente(long idCuenta, int idCliente,
                                               long idNegocio, RolUsuario rol) {
                        throw new IllegalStateException("MySQL no disponible");
                    }
                });

        String token = proveedor.emitir(7, 42, 3L, "CLIENTE");

        assertThrows(IllegalStateException.class, () -> proveedor.validar(token));
    }

    private static final class RegistroRevocaciones implements IRevocacionToken {
        private int revocaciones;
        private long expiracion;

        @Override
        public void revocar(String token, long idCuenta, long expiraEnEpochSegundos) {
            revocaciones++;
            expiracion = expiraEnEpochSegundos;
        }

        @Override
        public boolean estaRevocado(String token) {
            return revocaciones > 0;
        }
    }

    private static final class ValidadorSesionFalso implements IValidadorSesion {
        private final boolean vigente;
        private long idCuenta;
        private int idCliente;
        private long idNegocio;
        private RolUsuario rol;

        private ValidadorSesionFalso(boolean vigente) {
            this.vigente = vigente;
        }

        @Override
        public boolean estaVigente(long idCuenta, int idCliente, long idNegocio, RolUsuario rol) {
            this.idCuenta = idCuenta;
            this.idCliente = idCliente;
            this.idNegocio = idNegocio;
            this.rol = rol;
            return vigente;
        }
    }
}
