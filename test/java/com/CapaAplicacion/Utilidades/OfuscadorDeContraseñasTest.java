package com.CapaAplicacion.Utilidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OfuscadorDeContraseñasTest {

    @Test
    @DisplayName("Invierte la contraseña e inserta el extra en la mitad")
    void ofuscadorEstricto() {
        String resultado = OfuscadorDeContraseñas.ofuscadorEstricto("123456", "XX");
        assertEquals("654XX321", resultado);
    }

    @Test
    @DisplayName("La longitud resultante es contraseña + extra")
    void longitud() {
        String resultado = OfuscadorDeContraseñas.ofuscadorEstricto("abcdefghi", "PEPPER");
        assertEquals("abcdefghi".length() + "PEPPER".length(), resultado.length());
    }

    @Test
    @DisplayName("Contraseña nula: no lanza error y el resultado conserva el tamaño reservado")
    void contrasenaNula() {
        String resultado = OfuscadorDeContraseñas.ofuscadorEstricto(null, "X");
        assertEquals("X".length(), resultado.length());
    }

    @Test
    @DisplayName("Maneja extra nulo como vacío")
    void extraNulo() {
        String resultado = OfuscadorDeContraseñas.ofuscadorEstricto("abc", null);
        assertEquals("cba", resultado);
    }

    @Test
    @DisplayName("Es determinista: mismo input, mismo output")
    void determinismo() {
        String a = OfuscadorDeContraseñas.ofuscadorEstricto("Password123", "Pepper");
        String b = OfuscadorDeContraseñas.ofuscadorEstricto("Password123", "Pepper");
        assertEquals(a, b);
    }
}