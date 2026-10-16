package com.CapaAplicacion.Interfaces;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.CapaDominio.Entidades.Auditoria;
public interface  IServiceAuditoria {

    CompletableFuture<Void> registrarAuditoria(Auditoria a);

    CompletableFuture<Auditoria> buscarPorId(long idNegocio, int id);


    CompletableFuture<List<Auditoria>> obtenerTodos(long idNegocio);

    CompletableFuture<Void> actualizar(Auditoria a);


    CompletableFuture<Void> eliminar(int id);

 
}
