package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaAplicacion.Interfaces.INotificadorEmail;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaInfraestructura.Implementacion.NotificacionListener;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class NotificacionBusIntegrationTest {

    @Mock
    private INotificadorEmail notificadorMock;

    @Mock
    private IAuditoriaRepository auditoriaRepositoryMock;

    private Venta crearVentaMock(int id) {
        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setId(id);
        return venta;
    }

    @Test
    @DisplayName("QA/Dev Senior: Valida flujo completo, eventos por tipo, listeners, adaptadores e hilos separados")
    void testFlujoCompletoEventBusNotificacionYAuditoria() throws Exception {
        // 1. Instanciación del bus compartido (In Memory Event Bus)
        InMemoryEventBus eventBus = new InMemoryEventBus();

        // 2. Inicialización de los Listeners/Servicios suscritos al EventBus
        NotificacionListener notificacionListener = new NotificacionListener(eventBus, notificadorMock);
        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositoryMock, eventBus);

        // 3. Publicación de eventos de diferentes tipos de operación
        Venta ventaCreada = crearVentaMock(101);
        Venta ventaPagada = crearVentaMock(102);
        Venta ventaCancelada = crearVentaMock(103);

        System.out.println("🚀 [TEST] Publicando eventos en el InMemoryEventBus...");
        eventBus.publicar(new VentasEventos("CREADA", ventaCreada));
        eventBus.publicar(new VentasEventos("PAGADA", ventaPagada));
        eventBus.publicar(new VentasEventos("CANCELADA", ventaCancelada));

        // 4. Verificación de llamadas al Adaptador de Notificaciones según tipo de operación
        verify(notificadorMock, timeout(2000).times(1)).enviarConfirmacionVenta(argThat(v -> v.getId() == 101));
        verify(notificadorMock, timeout(2000).times(1)).enviarNotificacionPago(argThat(v -> v.getId() == 102));
        // CANCELED no envía email (según NotificacionListener)
        verify(notificadorMock, never()).enviarConfirmacionVenta(argThat(v -> v.getId() == 103));

        // 5. Verificación de registro de auditoría (Listener de auditoría)
        ArgumentCaptor<Auditoria> auditoriaCaptor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepositoryMock, timeout(2000).atLeast(3)).guardar(auditoriaCaptor.capture());

        boolean encontroCreada = false;
        boolean encontroPagada = false;
        boolean encontroCancelada = false;

        for (Auditoria aud : auditoriaCaptor.getAllValues()) {
            if (aud.getIdEntidad() == 101 && aud.getAccion() == AccionAuditoria.REGISTRAR_VENTA) encontroCreada = true;
            if (aud.getIdEntidad() == 102 && aud.getAccion() == AccionAuditoria.REGISTRAR_VENTA) encontroPagada = true;
            if (aud.getIdEntidad() == 103 && aud.getAccion() == AccionAuditoria.CANCELAR_VENTA) encontroCancelada = true;
        }

        assertTrue(encontroCreada, "Debe registrar auditoría para venta CREADA");
        assertTrue(encontroPagada, "Debe registrar auditoría para venta PAGADA");
        assertTrue(encontroCancelada, "Debe registrar auditoría para venta CANCELADA");

        System.out.println("✅ [TEST] ¡Todas las verificaciones del bus, adaptadores e hilos pasaron con éxito!");
    }
}
