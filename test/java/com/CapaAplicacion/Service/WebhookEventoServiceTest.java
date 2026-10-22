package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.IEventoWebhookRepository;
import com.CapaAplicacion.Interfaces.IProcesadorEventoWebhook;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.CapaDominio.Excepciones.WebhookProcessingException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookEventoServiceTest {

    @Test
    void procesaYMarchaComoProcesado() {
        IEventoWebhookRepository repositorio = mock(IEventoWebhookRepository.class);
        IProcesadorEventoWebhook procesador = mock(IProcesadorEventoWebhook.class);
        EventoWebhook evento = evento();
        when(repositorio.listarPendientes(10)).thenReturn(List.of(evento));
        when(repositorio.marcarProcesando(1)).thenReturn(true);

        WebhookEventoService service = servicio(repositorio, procesador, 3);

        assertEquals(1, service.procesarPendientes());
        verify(procesador).procesar(evento);
        verify(repositorio).marcarProcesado(1);
    }

    @Test
    void reprogramaFalloReintentable() {
        IEventoWebhookRepository repositorio = mock(IEventoWebhookRepository.class);
        IProcesadorEventoWebhook procesador = mock(IProcesadorEventoWebhook.class);
        EventoWebhook evento = evento();
        when(repositorio.listarPendientes(10)).thenReturn(List.of(evento));
        when(repositorio.marcarProcesando(1)).thenReturn(true);
        org.mockito.Mockito.doThrow(new WebhookProcessingException("temporal", true))
                .when(procesador).procesar(evento);

        WebhookEventoService service = servicio(repositorio, procesador, 3);

        assertEquals(0, service.procesarPendientes());
        verify(repositorio).marcarReintento(1, "temporal");
        verify(repositorio, never()).marcarProcesado(anyInt());
    }

    @Test
    void marcaFallidoAlAgotarIntentos() {
        IEventoWebhookRepository repositorio = mock(IEventoWebhookRepository.class);
        IProcesadorEventoWebhook procesador = mock(IProcesadorEventoWebhook.class);
        EventoWebhook evento = evento();
        evento.setIntentos(2);
        when(repositorio.listarPendientes(10)).thenReturn(List.of(evento));
        when(repositorio.marcarProcesando(1)).thenReturn(true);
        org.mockito.Mockito.doThrow(new WebhookProcessingException("temporal", true))
                .when(procesador).procesar(evento);

        WebhookEventoService service = servicio(repositorio, procesador, 3);

        assertEquals(0, service.procesarPendientes());
        verify(repositorio).marcarFallido(1, "temporal");
        verify(repositorio, never()).marcarReintento(anyInt(), anyString());
    }

    @Test
    void errorPermanenteNoSeReintenta() {
        IEventoWebhookRepository repositorio = mock(IEventoWebhookRepository.class);
        IProcesadorEventoWebhook procesador = mock(IProcesadorEventoWebhook.class);
        EventoWebhook evento = evento();
        when(repositorio.listarPendientes(10)).thenReturn(List.of(evento));
        when(repositorio.marcarProcesando(1)).thenReturn(true);
        org.mockito.Mockito.doThrow(new WebhookProcessingException("inválido", false))
                .when(procesador).procesar(evento);

        WebhookEventoService service = servicio(repositorio, procesador, 3);

        assertEquals(0, service.procesarPendientes());
        verify(repositorio).marcarFallido(1, "inválido");
    }

    @Test
    void registraEventoNuevoYReportaDuplicado() {
        IEventoWebhookRepository repositorio = mock(IEventoWebhookRepository.class);
        IProcesadorEventoWebhook procesador = mock(IProcesadorEventoWebhook.class);
        when(repositorio.registrar(any(EventoWebhook.class))).thenReturn(true, false);

        WebhookEventoService service = servicio(repositorio, procesador, 3);

        assertTrue(service.registrar(
                ProveedorWebhook.MERCADO_PAGO, "payment", "123", "{}"));
        assertFalse(service.registrar(
                ProveedorWebhook.MERCADO_PAGO, "payment", "123", "{}"));
    }

    private WebhookEventoService servicio(IEventoWebhookRepository repositorio,
                                          IProcesadorEventoWebhook procesador,
                                          int maxIntentos) {
        return new WebhookEventoService(
                repositorio, procesador, mock(IServiceExecutor.class), maxIntentos, 10);
    }

    private EventoWebhook evento() {
        EventoWebhook evento = new EventoWebhook(
                ProveedorWebhook.MERCADO_PAGO, "payment", "123", "{}");
        evento.setId(1);
        return evento;
    }
}
