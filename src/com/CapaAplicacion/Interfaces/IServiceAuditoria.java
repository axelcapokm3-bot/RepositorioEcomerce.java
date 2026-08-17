package com.CapaAplicacion.Interfaces;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.CapaDominio.Entidades.Auditoria;
public interface  IServiceAuditoria {

    CompletableFuture<Void> registrarAuditoria(Auditoria a);

    CompletableFuture<Auditoria> buscarPorId(int id);


    CompletableFuture<List<Auditoria>> obtenerTodos();

    CompletableFuture<Void> actualizar(Auditoria a);


    CompletableFuture<Void> eliminar(int id);

 
}
