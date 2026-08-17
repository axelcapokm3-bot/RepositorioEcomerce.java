package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private IAuditoriaRepository auditoriaRepository;

    private Venta ventaConId() {
        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setId(5);
        return venta;
    }

    @Test
    @DisplayName("Al suscribirse, procesa un evento de venta y guarda la auditoría")
    void procesaEventoDeVenta() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AuditoriaService service = new AuditoriaService(auditoriaRepository, eventBus);

        eventBus.publicar(new VentasEventos("PAGADA", ventaConId()));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository, timeout(1000)).guardar(captor.capture());
        assertEquals(AccionAuditoria.REGISTRAR_VENTA, captor.getValue().getAccion());
        assertEquals(5, captor.getValue().getIdEntidad());
    }

    @Test
    @DisplayName("Un evento de cancelación guarda auditoría de cancelación")
    void procesaEventoDeCancelacion() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AuditoriaService service = new AuditoriaService(auditoriaRepository, eventBus);

        eventBus.publicar(new VentasEventos("CANCELADA", ventaConId()));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository, timeout(1000)).guardar(captor.capture());
        assertEquals(AccionAuditoria.CANCELAR_VENTA, captor.getValue().getAccion());
    }

    @Test
    @DisplayName("registrarAuditoriaAsync: persiste el registro")
    void registrarAuditoria() throws Exception {
        AuditoriaService service = new AuditoriaService(auditoriaRepository, new InMemoryEventBus());
        Auditoria auditoria = new Auditoria(AccionAuditoria.CREAR, 3, "Se creo un producto");

        service.registrarAuditoriaAsync(auditoria).get();
        verify(auditoriaRepository).guardar(auditoria);
    }

    @Test
    @DisplayName("obtenerTodosAsync: devuelve todos los registros")
    void obtenerTodos() throws Exception {
        AuditoriaService service = new AuditoriaService(auditoriaRepository, new InMemoryEventBus());
        Auditoria a = new Auditoria(AccionAuditoria.CREAR, 1, "desc");
        when(auditoriaRepository.obtenerTodos()).thenReturn(List.of(a));

        List<Auditoria> resultado = service.obtenerTodosAsync().get();
        assertEquals(1, resultado.size());
    }

    @Test
    @DisplayName("obtenerPorAccionAsync: filtra por acción")
    void obtenerPorAccion() throws Exception {
        AuditoriaService service = new AuditoriaService(auditoriaRepository, new InMemoryEventBus());
        when(auditoriaRepository.obtenerPorAccion(AccionAuditoria.ELIMINAR)).thenReturn(List.of());

        assertEquals(0, service.obtenerPorAccionAsync(AccionAuditoria.ELIMINAR).get().size());
    }

    @Test
    @DisplayName("obtenerPorProductoAsync: filtra por entidad")
    void obtenerPorEntidad() throws Exception {
        AuditoriaService service = new AuditoriaService(auditoriaRepository, new InMemoryEventBus());
        when(auditoriaRepository.obtenerPorEntidad(9)).thenReturn(List.of());

        assertEquals(0, service.obtenerPorProductoAsync(9).get().size());
    }
}