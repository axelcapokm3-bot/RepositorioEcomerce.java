package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class AuditoriaMockIntegrationTest {

    @Mock
    private IAuditoriaRepository auditoriaRepositoryMock;

    @Test
    @DisplayName("Mock Test: Verifica flujo completo de auditoría y persistencia con metadatos extendidos")
    void testAuditoriaConMocksYEventBus() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositoryMock, eventBus);

        // Simulamos una venta para el evento
        Venta venta = new Venta(1, TipoDePago.TARJETA_CREDITO);
        venta.setId(88);

        System.out.println("🚀 [MOCK TEST] Publicando evento VENTA_PAGADA en el EventBus...");
        eventBus.publicar(new VentasEventos("PAGADA", venta));

        // Capturamos el objeto Auditoria enviado al repositorio mock
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepositoryMock, timeout(2000).times(1)).guardar(captor.capture());

        Auditoria auditoriaGuardada = captor.getValue();
        assertNotNull(auditoriaGuardada, "El objeto de auditoría no debe ser nulo");
        assertEquals(88, auditoriaGuardada.getIdEntidad());
        assertEquals("Venta", auditoriaGuardada.getEntidad());
        assertEquals("OK", auditoriaGuardada.getResultado());
        assertNotNull(auditoriaGuardada.getFecha());

        // Verificamos métodos de consulta con Mockito
        when(auditoriaRepositoryMock.buscarPorEntidad(1L, "Venta", 88)).thenReturn(List.of(auditoriaGuardada));
        List<Auditoria> resultadoBusqueda = auditoriaService.obtenerPorProductoAsync(1L, 88).get();
        
        assertEquals(1, resultadoBusqueda.size());
        assertEquals(88, resultadoBusqueda.get(0).getIdEntidad());

        System.out.println("✅ [MOCK TEST] ¡Pruebas con Mockito para auditoría pasaron exitosamente!");
    }
}
