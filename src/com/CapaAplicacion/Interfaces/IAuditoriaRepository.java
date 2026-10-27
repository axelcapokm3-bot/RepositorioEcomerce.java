package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface IAuditoriaRepository {
    void guardar(Auditoria auditoria) throws SQLException;
    List<Auditoria> buscarPorEntidad(long idNegocio, String entidad, int idEntidad);
    List<Auditoria> buscarPorUsuario(long idNegocio, String usuario);
    List<Auditoria> buscarPorAccion(long idNegocio, AccionAuditoria accion);
    List<Auditoria> buscarPorRangoFecha(long idNegocio, LocalDateTime desde, LocalDateTime hasta);
    List<Auditoria> listarTodas(long idNegocio);
    default long contarTotal(long idNegocio) {
        throw new UnsupportedOperationException(
                "El repositorio debe implementar contarTotal con SELECT COUNT(*) en la base de datos.");
    }
    default List<Auditoria> listarPaginado(long idNegocio, int limite, int offset) {
        throw new UnsupportedOperationException(
                "El repositorio debe implementar listarPaginado con LIMIT ? OFFSET ? en la base de datos.");
    }
}
