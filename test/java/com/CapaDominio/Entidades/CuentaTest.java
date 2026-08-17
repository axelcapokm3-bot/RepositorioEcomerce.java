package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuentaTest {

    private Cuenta cuentaValida() {
        return new Cuenta(1, "jperez", "jperez@gmail.com", "Password123", "CLIENTE", 10, new BigDecimal("1000"));
    }

    @Test
    @DisplayName("Constructor: crea cuenta válida")
    void constructorValido() {
        Cuenta c = cuentaValida();
        assertEquals(1, c.getId());
        assertEquals("jperez", c.getNombreCuenta());
        assertEquals(new BigDecimal("1000"), c.getSaldo());
        assertTrue(c.tieneRol("cliente"));
    }

    @Test
    @DisplayName("Constructor: rechaza datos inválidos")
    void constructorRechazaInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(-1, "a", "a@b.com", "Password123", "CLIENTE", 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "", "a@b.com", "Password123", "CLIENTE", 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "a", "correo-sin-arroba", "Password123", "CLIENTE", 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "a", "a@b.com", "123", "CLIENTE", 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "a", "a@b.com", "Password123", "", 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "a", "a@b.com", "Password123", "CLIENTE", 0, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Cuenta(1, "a", "a@b.com", "Password123", "CLIENTE", 1, new BigDecimal("-1")));
    }

    @Test
    @DisplayName("Acreditar: suma saldo y valida monto")
    void acreditar() {
        Cuenta c = cuentaValida();
        c.acreditar(new BigDecimal("500"));
        assertEquals(new BigDecimal("1500"), c.getSaldo());
        assertThrows(IllegalArgumentException.class, () -> c.acreditar(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> c.acreditar(null));
    }

    @Test
    @DisplayName("Debitar: resta saldo y lanza error si no alcanza")
    void debitar() {
        Cuenta c = cuentaValida();
        c.debitar(new BigDecimal("300"));
        assertEquals(new BigDecimal("700"), c.getSaldo());
        assertThrows(IllegalStateException.class, () -> c.debitar(new BigDecimal("99999")));
        assertThrows(IllegalArgumentException.class, () -> c.debitar(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Transferir: mueve saldo entre cuentas")
    void transferirA() {
        Cuenta origen = cuentaValida();
        Cuenta destino = new Cuenta(2, "mgomez", "mgomez@gmail.com", "Password123", "CLIENTE", 11, new BigDecimal("100"));
        origen.transferirA(destino, new BigDecimal("400"));
        assertEquals(new BigDecimal("600"), origen.getSaldo());
        assertEquals(new BigDecimal("500"), destino.getSaldo());
    }

    @Test
    @DisplayName("Transferir: rechaza a la misma cuenta o cuenta nula")
    void transferirInvalida() {
        Cuenta c = cuentaValida();
        assertThrows(IllegalArgumentException.class, () -> c.transferirA(c, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> c.transferirA(null, BigDecimal.TEN));
        assertThrows(IllegalStateException.class, () -> c.transferirA(
                new Cuenta(2, "b", "b@b.com", "Password123", "CLIENTE", 11, BigDecimal.TEN), new BigDecimal("99999")));
    }

    @Test
    @DisplayName("Roles: agregar, remover y verificar")
    void roles() {
        Cuenta c = cuentaValida();
        c.agregarRol("ADMIN");
        assertTrue(c.tieneRol("admin"));
        c.removerRol("ADMIN");
        assertFalse(c.tieneRol("admin"));
        assertFalse(c.tieneRol(null));
    }

    @Test
    @DisplayName("El ID solo puede asignarse una vez")
    void setIdUnaSolaVez() {
        Cuenta c = new Cuenta(0, "nuevo", "nuevo@gmail.com", "Password123", "CLIENTE", 1, BigDecimal.ZERO);
        c.setId(5);
        assertEquals(5, c.getId());
        assertThrows(IllegalStateException.class, () -> c.setId(6));
    }
}
