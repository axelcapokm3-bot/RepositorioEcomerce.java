package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteTest {

    private Cliente clienteMayor() {
        return new Cliente(1, "34567890", "Juan", "Perez", "Av. Siempre 123",
                LocalDate.now().minusYears(30), "Springfield");
    }

    @Test
    @DisplayName("Constructor: crea cliente válido")
    void constructorValido() {
        Cliente c = clienteMayor();
        assertEquals("Juan", c.getNombre());
        assertEquals("Perez", c.getApellido());
        assertTrue(c.isActivo());
        assertEquals("", c.getCuit());
    }

    @Test
    @DisplayName("Constructor: rechaza datos inválidos")
    void constructorRechazaInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(-1, "34567890", "Juan", "Perez", "Dir", LocalDate.now().minusYears(30), "Loc"));
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1, "", "Juan", "Perez", "Dir", LocalDate.now().minusYears(30), "Loc"));
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1, "34567890", "", "Perez", "Dir", LocalDate.now().minusYears(30), "Loc"));
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1, "34567890", "Juan", "", "Dir", LocalDate.now().minusYears(30), "Loc"));
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1, "34567890", "Juan", "Perez", "Dir", null, "Loc"));
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1, "34567890", "Juan", "Perez", "Dir", LocalDate.now().plusDays(1), "Loc"));
    }

    @Test
    @DisplayName("Mayoría de edad según fecha de nacimiento")
    void esMayorDeEdad() {
        assertTrue(clienteMayor().esMayorDeEdad());
        Cliente menor = new Cliente(2, "34567890", "Ana", "Gomez", "Dir",
                LocalDate.now().minusYears(10), "Loc");
        assertFalse(menor.esMayorDeEdad());
    }

    @Test
    @DisplayName("Dar de baja: solo una vez y con motivo obligatorio")
    void darDeBaja() {
        Cliente c = clienteMayor();
        c.darDeBaja("Fraude detectado");
        assertFalse(c.isActivo());
        assertThrows(IllegalStateException.class, () -> c.darDeBaja("Otra vez"));
        Cliente c2 = clienteMayor();
        assertThrows(IllegalArgumentException.class, () -> c2.darDeBaja(""));
        assertThrows(IllegalArgumentException.class, () -> c2.darDeBaja(null));
    }

    @Test
    @DisplayName("Reactivar: vuelve a activo y limpia el motivo")
    void reactivar() {
        Cliente c = clienteMayor();
        c.darDeBaja("Motivo");
        c.reactivar();
        assertTrue(c.isActivo());
        assertThrows(IllegalStateException.class, () -> c.reactivar());
    }

    @Test
    @DisplayName("Asignar CUIT: acepta 11 dígitos y rechaza inválidos")
    void asignarElCuit() {
        Cliente c = clienteMayor();
        c.asignarElCuit("20345678909");
        assertEquals("20345678909", c.getCuit());
        assertThrows(IllegalArgumentException.class, () -> c.asignarElCuit("123"));
        assertThrows(IllegalArgumentException.class, () -> c.asignarElCuit("1234567890a"));
        assertThrows(IllegalArgumentException.class, () -> c.asignarElCuit(""));
    }
}