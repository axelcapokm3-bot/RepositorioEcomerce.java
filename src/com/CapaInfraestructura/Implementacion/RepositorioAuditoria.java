package com.CapaInfraestructura.Implementacion;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaAplicacion.Interfaces.IAuditoriaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RepositorioAuditoria implements IAuditoriaRepository {
    
private static final List<Auditoria> logs = new CopyOnWriteArrayList<>();

private static int maximoId() {
    if (logs.isEmpty()) {
        return 1;
    }
    int maxId = 0;
    for (Auditoria log : logs) {
        if (log.getId() > maxId) {
            maxId = log.getId();
        }
    }
    return maxId + 1;
}

private static final AtomicInteger siguienteId = new AtomicInteger(maximoId());

    public void guardar(Auditoria log) {
        if (log == null) {
            throw new IllegalArgumentException("El registro de auditoría no puede ser nulo.");
        }
        if (log.getId() == 0) {
            siguienteId.accumulateAndGet(maximoId(), Math::max);
            log.setId(siguienteId.getAndIncrement());
        }
        logs.add(log);
    }

    public List<Auditoria> obtenerTodos() {
        return new ArrayList<>(logs);
    }

  public List<Auditoria> obtenerPorEntidad(int idProducto) {
    List<Auditoria> listaA = new ArrayList<>(); 

    for (Auditoria A : logs) {
       
        if (A.getIdEntidad() == idProducto) {
            listaA.add(A); 
        }
    }
    return listaA;
}

  @Override
  public List<Auditoria> obtenerPorAccion(AccionAuditoria accion) {
    List<Auditoria> resultado = new ArrayList<>();
    for (Auditoria auditoria : logs) {
      if (auditoria.getAccion() == accion) {
        resultado.add(auditoria);
      }
    }
    return resultado;
  }

}
