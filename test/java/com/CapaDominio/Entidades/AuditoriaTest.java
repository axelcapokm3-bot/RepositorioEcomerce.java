package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditoriaTest {

    @Test
    @DisplayName("Constructor: crea registro válido")
    void auditoriaValida() {
        Auditoria a = new Auditoria(AccionAuditoria.REGISTRAR_VENTA, 7, "la operacion es PAGADA");
        assertEquals(AccionAuditoria.REGISTRAR_VENTA, a.getAccion());
        assertEquals(7, a.getIdEntidad());
        assertTrue(a.esDelDiaDeHoy());
        assertNotNull(a.getFecha());
    }

    @Test
    @DisplayName("Constructor: rechaza acción, descripción o ID inválidos")
    void auditoriaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Auditoria(null, 1, "desc"));
        assertThrows(IllegalArgumentException.class, () -> new Auditoria(AccionAuditoria.CREAR, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new Auditoria(AccionAuditoria.CREAR, 1, "  "));
        assertThrows(IllegalArgumentException.class, () -> new Auditoria(AccionAuditoria.CREAR, -1, "desc"));
    }

    @Test
    @DisplayName("Factory y formateo de log")
    void factoryYLog() {
        Auditoria a = Auditoria.registrar(AccionAuditoria.CREAR, 3, "Se creo un producto");
        assertNotNull(a.formatearParaLog());
    }

    @Test
    @DisplayName("El ID del registro solo puede asignarse una vez")
    void setIdUnaSolaVez() {
        Auditoria a = new Auditoria(AccionAuditoria.CREAR, 1, "desc");
        a.setId(4);
        assertEquals(4, a.getId());
        assertThrows(IllegalStateException.class, () -> a.setId(5));
    }
}