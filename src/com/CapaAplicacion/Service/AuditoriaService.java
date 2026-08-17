package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.*;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaDominio.Entidades.Venta;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;

public class AuditoriaService implements IAuditoriaService {
    private final IAuditoriaRepository auditoriaRepository;
    private final EventBus eventBus;

    public AuditoriaService(IAuditoriaRepository auditoriaRepository, EventBus eventBus) {
        this.auditoriaRepository = auditoriaRepository;
        this.eventBus = eventBus;

        this.eventBus.suscribirse(VentasEventos.class, new Consumer<VentasEventos>() {
            @Override
            public void accept(VentasEventos evento) {
                AuditoriaService.this.procesarEventoVentaAsync(evento);
            }
        });
    }


    private CompletableFuture<Void> procesarEventoVentaAsync(final VentasEventos evento) {
            if (evento == null || evento.getVentaSnapshot() == null) {
    System.err.println("Evento o snapshot de venta inválido.");
return CompletableFuture.completedFuture(null);
}



        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Venta venta = evento.getVentaSnapshot();
                    String tipoEvento = evento.getTipoOperacion();

                    AccionAuditoria accion = null;

        
                    if ("CREADA".equalsIgnoreCase(tipoEvento)) {
                        accion = AccionAuditoria.REGISTRAR_VENTA;
                    } else if ("PAGADA".equalsIgnoreCase(tipoEvento)) {
                        accion = AccionAuditoria.REGISTRAR_VENTA;
                    } else if ("CANCELADA".equalsIgnoreCase(tipoEvento)) {
                        accion = AccionAuditoria.CANCELAR_VENTA;
                    } else {
                        accion = AccionAuditoria.ACTUALIZAR;
                    }

                    Auditoria registro = new Auditoria(accion, venta.getId(),"la operacion es" + tipoEvento);

                    auditoriaRepository.guardar(registro);
                } catch (Exception e) {
                    System.err.println("Error procesando log de auditoría: " + e.getMessage());
                }
            }
        });
    }


    @Override
    public CompletableFuture<Void> registrarAuditoriaAsync(final Auditoria auditoria) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                auditoriaRepository.guardar(auditoria);
            }
        });
    }

 
    @Override
    public CompletableFuture<List<Auditoria>> obtenerTodosAsync() {
        return CompletableFuture.supplyAsync(new Supplier<List<Auditoria>>() {
            @Override
            public List<Auditoria> get() {
                return auditoriaRepository.obtenerTodos();
            }
        });
    }

 
    @Override
    public CompletableFuture<List<Auditoria>> obtenerPorProductoAsync(final int idProducto) {
        return CompletableFuture.supplyAsync(new Supplier<List<Auditoria>>() {
            @Override
            public List<Auditoria> get() {
                return auditoriaRepository.obtenerPorEntidad(idProducto);
            }
        });
    }

    @Override
    public CompletableFuture<List<Auditoria>> obtenerPorAccionAsync(final AccionAuditoria accion) {
        return CompletableFuture.supplyAsync(new Supplier<List<Auditoria>>() {
            @Override
            public List<Auditoria> get() {
                return auditoriaRepository.obtenerPorAccion(accion);
            }
        });
    }

}

