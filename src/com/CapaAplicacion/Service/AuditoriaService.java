package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.*;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaDominio.Entidades.Venta;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;

public class AuditoriaService implements IAuditoriaService {
    private final IAuditoriaRepository auditoriaRepository;
    private final EventBus eventBus;
    private final IServiceExecutor executor;

    public AuditoriaService(IAuditoriaRepository auditoriaRepository, EventBus eventBus) {
        this.auditoriaRepository = auditoriaRepository;
        this.eventBus = eventBus;
        this.executor = RegistroExecutors.obtener(TipoServicio.AUDITORIA);

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



        return executor.ejecutar(new Runnable() {
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
                    registro.setIdNegocio(venta.getIdNegocio());

                    auditoriaRepository.guardar(registro);
                } catch (Exception e) {
                    System.err.println("Error procesando log de auditoría: "
                            + SanitizadorLog.error(e));
                }
            }
        });
    }


    @Override
    public CompletableFuture<Void> registrarAuditoriaAsync(final Auditoria auditoria) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                try {
                    auditoriaRepository.guardar(auditoria);
                } catch (Exception e) {
                    System.err.println("Error al guardar auditoría: " + SanitizadorLog.error(e));
                }
            }
        });
    }

 
    @Override
    public CompletableFuture<List<Auditoria>> obtenerTodosAsync(final long idNegocio) {
        return executor.ejecutar(new java.util.concurrent.Callable<List<Auditoria>>() {
            @Override
            public List<Auditoria> call() {
                return auditoriaRepository.listarTodas(idNegocio);
            }
        });
    }

 
    @Override
    public CompletableFuture<List<Auditoria>> obtenerPorProductoAsync(final long idNegocio, final int idProducto) {
        return executor.ejecutar(new java.util.concurrent.Callable<List<Auditoria>>() {
            @Override
            public List<Auditoria> call() {
                return auditoriaRepository.buscarPorEntidad(idNegocio, "Venta", idProducto);
            }
        });
    }

    @Override
    public CompletableFuture<List<Auditoria>> obtenerPorAccionAsync(final long idNegocio, final AccionAuditoria accion) {
        return executor.ejecutar(new java.util.concurrent.Callable<List<Auditoria>>() {
            @Override
            public List<Auditoria> call() {
                return auditoriaRepository.buscarPorAccion(idNegocio, accion);
            }
        });
    }

}
