package com.CapaAplicacion.Interfaces;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import java.util.List;


public interface IAuditoriaRepository {
 
 public    void guardar(Auditoria log); 
 public    List<Auditoria> obtenerTodos();
 public   List<Auditoria> obtenerPorEntidad(int idEntidad);
 public   List<Auditoria> obtenerPorAccion(AccionAuditoria accion);
}