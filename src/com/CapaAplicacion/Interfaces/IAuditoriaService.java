package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAuditoriaService {

    CompletableFuture<Void> registrarAuditoriaAsync(Auditoria auditoria);


    CompletableFuture<List<Auditoria>> obtenerTodosAsync();

    CompletableFuture<List<Auditoria>> obtenerPorProductoAsync(int idProducto);

    CompletableFuture<List<Auditoria>> obtenerPorAccionAsync(AccionAuditoria accion);
}