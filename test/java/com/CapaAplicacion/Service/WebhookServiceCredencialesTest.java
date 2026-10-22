package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.TipoDePago;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookServiceCredencialesTest {

    private static final String CLAVE_TOKEN = "mercadopago.access-token";

    @Test
    void laConsultaDeOrderUsaCredencialPorOperacion() throws Exception {
        String tokenAnterior = System.getProperty(CLAVE_TOKEN);
        String globalAnterior = MercadoPagoConfig.getAccessToken();
        try {
            System.setProperty(CLAVE_TOKEN, "token-webhook");

            Pago pago = mock(Pago.class);
            when(pago.getIdNegocio()).thenReturn(1L);
            when(pago.getPasarela()).thenReturn(TipoDePago.MERCADO_PAGO.name());
            IPagoRepository pagoRepository = mock(IPagoRepository.class);
            when(pagoRepository.buscarPorOrderId("order-123")).thenReturn(Optional.of(pago));

            OrderClient orderClient = mock(OrderClient.class);
            when(orderClient.get(eq("order-123"), any(MPRequestOptions.class)))
                    .thenThrow(new RuntimeException("corte de prueba"));

            WebhookService webhookService = new WebhookService(
                    mock(INegocioRepository.class),
                    pagoRepository,
                    mock(IVentaRepository.class),
                    mock(EventBus.class),
                    new EjecutorDirecto(),
                    mock(PaymentClient.class),
                    orderClient);

            webhookService.procesar("order", "order-123");

            ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
            verify(orderClient).get(eq("order-123"), opciones.capture());
            assertEquals("token-webhook", opciones.getValue().getAccessToken());
            assertEquals(globalAnterior, MercadoPagoConfig.getAccessToken(),
                    "No debe mutarse el estado global del SDK de Mercado Pago");
        } finally {
            if (tokenAnterior == null) {
                System.clearProperty(CLAVE_TOKEN);
            } else {
                System.setProperty(CLAVE_TOKEN, tokenAnterior);
            }
        }
    }

    private static final class EjecutorDirecto implements IServiceExecutor {
        @Override
        public CompletableFuture<Void> ejecutar(Runnable tarea) {
            tarea.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> ejecutar(Callable<T> tarea) {
            try {
                return CompletableFuture.completedFuture(tarea.call());
            } catch (Exception e) {
                CompletableFuture<T> fallido = new CompletableFuture<>();
                fallido.completeExceptionally(e);
                return fallido;
            }
        }

        @Override
        public int getColaActual() {
            return 0;
        }

        @Override
        public int getHiloActivos() {
            return 0;
        }

        @Override
        public int getHilosTotales() {
            return 1;
        }

        @Override
        public TipoServicio getTipo() {
            return TipoServicio.NOTIFICACIONES;
        }

        @Override
        public void shutdown() {
        }
    }
}
