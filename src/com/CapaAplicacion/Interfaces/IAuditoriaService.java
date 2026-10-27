package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAuditoriaService {

    CompletableFuture<Void> registrarAuditoriaAsync(Auditoria auditoria);


    CompletableFuture<List<Auditoria>> obtenerTodosAsync(long idNegocio);
    CompletableFuture<List<Auditoria>> obtenerTodosAsync(long idNegocio, int limite, int offset);
    CompletableFuture<Long> contarTotalAsync(long idNegocio);

    CompletableFuture<List<Auditoria>> obtenerPorProductoAsync(long idNegocio, int idProducto);

    CompletableFuture<List<Auditoria>> obtenerPorAccionAsync(long idNegocio, AccionAuditoria accion);
}