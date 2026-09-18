package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface IAuditoriaRepository {
    void guardar(Auditoria auditoria) throws SQLException;
    List<Auditoria> buscarPorEntidad(String entidad, int idEntidad);
    List<Auditoria> buscarPorUsuario(String usuario);
    List<Auditoria> buscarPorAccion(AccionAuditoria accion);
    List<Auditoria> buscarPorRangoFecha(LocalDateTime desde, LocalDateTime hasta);
    List<Auditoria> listarTodas();
}
