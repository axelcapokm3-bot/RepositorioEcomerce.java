package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepositorioAuditoriaTest {

    @Test
    @DisplayName("filtra la auditoría por negocio en todas las consultas")
    void filtraPorNegocio() {
        RepositorioAuditoria repositorio = new RepositorioAuditoria();
        Auditoria delUno = new Auditoria(AccionAuditoria.CREAR, 1, "producto uno");
        delUno.setIdNegocio(1L);
        delUno.setUsuario("admin");
        Auditoria delDos = new Auditoria(AccionAuditoria.CREAR, 2, "producto dos");
        delDos.setIdNegocio(2L);
        delDos.setUsuario("admin");
        repositorio.guardar(delUno);
        repositorio.guardar(delDos);

        assertEquals(List.of(delUno), repositorio.listarTodas(1L));
        assertEquals(List.of(delDos), repositorio.buscarPorEntidad(2L, "Venta", 2));
        assertEquals(1, repositorio.buscarPorUsuario(1L, "admin").size());
        assertEquals(1, repositorio.buscarPorAccion(2L, AccionAuditoria.CREAR).size());
        assertEquals(1, repositorio.buscarPorRangoFecha(1L,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1)).size());
    }

    @Test
    @DisplayName("exige un negocio válido en todas las consultas")
    void exigeNegocio() {
        RepositorioAuditoria repositorio = new RepositorioAuditoria();

        assertThrows(IllegalArgumentException.class, () -> repositorio.listarTodas(0));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorEntidad(-1, "Venta", 1));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorUsuario(0, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> repositorio.buscarPorAccion(0, AccionAuditoria.CREAR));
        assertThrows(IllegalArgumentException.class,
                () -> repositorio.buscarPorRangoFecha(0, null, null));
    }
}
