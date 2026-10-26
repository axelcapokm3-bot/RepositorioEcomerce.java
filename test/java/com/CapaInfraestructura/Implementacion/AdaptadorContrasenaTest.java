package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class AdaptadorContrasenaTest {
    private final AdaptadorContrasena adaptador = new AdaptadorContrasena();

    @Test
    void verificaContraseniaCorrecta() {
        String hash = adaptador.generarHash("Segura123");
        assertTrue(adaptador.verificar("Segura123", hash));
    }

    @Test
    void rechazaContraseniaIncorrecta() {
        String hash = adaptador.generarHash("Segura123");
        assertFalse(adaptador.verificar("Otra4567", hash));
    }

    @Test
    void cadaHashUsaSalDistinta() {
        String primero = adaptador.generarHash("Segura123");
        String segundo = adaptador.generarHash("Segura123");
        assertNotEquals(primero, segundo);
        assertTrue(adaptador.verificar("Segura123", primero));
        assertTrue(adaptador.verificar("Segura123", segundo));
    }

    @Test
    void mantieneLosParametrosDeHardeningDocumentados() {
        String[] partes = adaptador.generarHash("Segura123").split("\\$");

        assertEquals(4, partes.length);
        assertEquals("pbkdf2", partes[0]);
        assertEquals("210000", partes[1]);
        assertEquals(16, Base64.getDecoder().decode(partes[2]).length);
        assertEquals(32, Base64.getDecoder().decode(partes[3]).length);
    }

    @Test
    void rechazaHashMalformadoONulo() {
        assertFalse(adaptador.verificar("Segura123", "no-es-un-hash"));
        assertFalse(adaptador.verificar("Segura123", null));
        assertFalse(adaptador.verificar(null, "pbkdf2$210000$abc$def"));
        assertFalse(adaptador.verificar("", "pbkdf2$210000$abc$def"));
    }
}
