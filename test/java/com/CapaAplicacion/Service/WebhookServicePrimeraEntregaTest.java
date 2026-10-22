package com.CapaAplicacion.Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.order.Order;
import com.mercadopago.resources.order.OrderPayment;
import com.mercadopago.resources.order.OrderTransaction;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookServicePrimeraEntregaTest {
    private static final long ID_NEGOCIO = 7L;
    private static final int ID_VENTA = 1;
    private static final String ORDER_ID = "1001";
    private static final String PAYMENT_ID = "5001";
    private static final String TOKEN = "token-negocio";

    private IPagoRepository pagoRepository;
    private IVentaRepository ventaRepository;
    private INegocioRepository negocioRepository;
    private PaymentClient paymentClient;
    private OrderClient orderClient;
    private EventBus eventBus;
    private Venta venta;
    private WebhookService service;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(IPagoRepository.class);
        ventaRepository = mock(IVentaRepository.class);
        negocioRepository = mock(INegocioRepository.class);
        paymentClient = mock(PaymentClient.class);
        orderClient = mock(OrderClient.class);
        eventBus = mock(EventBus.class);

        venta = new Venta(1, TipoDePago.MERCADO_PAGO);
        venta.setId(ID_VENTA);
        venta.setIdNegocio(ID_NEGOCIO);
        when(ventaRepository.buscarPorId(ID_NEGOCIO, ID_VENTA)).thenReturn(Optional.of(venta));
        when(ventaRepository.marcarComoPagada(ID_NEGOCIO, ID_VENTA)).thenReturn(true);
        when(negocioRepository.listarActivos()).thenReturn(List.of(negocio(ID_NEGOCIO)));
        service = servicio(id -> configuracion(TOKEN));
    }

    @Test
    void paymentSinOrderIdLocalSeResuelvePorReferenciaExterna() throws Exception {
        Pago pago = pago(10, null, null);
        prepararCandidatoUnico(pago);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "ARS"));
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "pending", "100.00", "0.00", null, null));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq(PAYMENT_ID), eq(ORDER_ID), eq(EstadoPago.PENDIENTE), anyString());
        verifyNoInteractions(eventBus);
    }

    @Test
    void orderSinOrderIdLocalSeResuelveConTokenUnico() throws Exception {
        Pago pago = pago(10, null, null);
        prepararCandidatoUnico(pago);
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "processed", "100.00", "100.00", PAYMENT_ID, "approved"));

        service.procesar("order", ORDER_ID);

        verify(orderClient, times(1)).get(eq(ORDER_ID), any(MPRequestOptions.class));
        verify(pagoRepository).actualizarWebhook(
                eq(10), eq(PAYMENT_ID), eq(ORDER_ID), eq(EstadoPago.APROBADO), anyString());
        verify(ventaRepository).marcarComoPagada(ID_NEGOCIO, ID_VENTA);
        verify(eventBus, times(1)).publicar(any(VentasEventos.class));
    }

    @Test
    void orderConTokensDistintosNoConsultaNiActualiza() throws Exception {
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(negocioRepository.listarActivos()).thenReturn(List.of(negocio(1), negocio(2)));
        Map<Long, ConfiguracionMercadoPago> configuraciones = Map.of(
                1L, configuracion("token-1"),
                2L, configuracion("token-2"));
        WebhookService servicioConTokens = servicio(configuraciones::get);

        servicioConTokens.procesar("order", ORDER_ID);

        verifyNoInteractions(orderClient);
        verify(pagoRepository, never()).buscarCandidatosMercadoPagoPorVenta(anyInt());
        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verify(ventaRepository, never()).marcarComoPagada(ID_NEGOCIO, ID_VENTA);
    }

    @Test
    void referenciaExternaInvalidaNoActualiza() throws Exception {
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("abc", "100.00", "ARS"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).buscarCandidatosMercadoPagoPorVenta(anyInt());
        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void multiplesPagosCandidatosNoActualizan() throws Exception {
        Pago primero = pago(10, null, null);
        Pago segundo = pago(11, null, null);
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarCandidatosMercadoPagoPorVenta(ID_VENTA))
                .thenReturn(List.of(primero, segundo));
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "ARS"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verify(ventaRepository, never()).marcarComoPagada(ID_NEGOCIO, ID_VENTA);
        verifyNoInteractions(orderClient);
    }

    @Test
    void importeIncorrectoNoActualiza() throws Exception {
        Pago pago = pago(10, null, null);
        prepararCandidatoUnico(pago);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "99.00", "ARS"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void monedaIncorrectaNoActualiza() throws Exception {
        Pago pago = pago(10, null, null);
        prepararCandidatoUnico(pago);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "USD"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void orderIdContradictorioNoActualiza() throws Exception {
        Pago pago = pago(10, "9999", null);
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarCandidatosMercadoPagoPorVenta(ID_VENTA))
                .thenReturn(List.of(pago));
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "ARS"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void paymentIdContradictorioEnLaOrderNoActualiza() throws Exception {
        Pago pago = pago(10, null, PAYMENT_ID);
        prepararCandidatoUnico(pago);
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "pending", "100.00", "0.00", "9999", "pending"));

        service.procesar("order", ORDER_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(eventBus);
    }

    @Test
    void notificacionRepetidaNoDuplicaEfectosNiEventos() throws Exception {
        Pago pago = pago(10, null, null);
        prepararCandidatoUnico(pago);
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "processed", "100.00", "100.00", PAYMENT_ID, "approved"));

        service.procesar("order", ORDER_ID);
        pago.setEstado(EstadoPago.APROBADO);
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.of(pago));
        service.procesar("order", ORDER_ID);

        verify(pagoRepository, times(1)).actualizarWebhook(
                eq(10), eq(PAYMENT_ID), eq(ORDER_ID), eq(EstadoPago.APROBADO), anyString());
        verify(ventaRepository, times(1)).marcarComoPagada(ID_NEGOCIO, ID_VENTA);
        verify(eventBus, times(1)).publicar(any(VentasEventos.class));
    }

    @Test
    void orderSinPaymentIdConservaElIdentificadorExistente() throws Exception {
        Pago pago = pago(10, null, PAYMENT_ID);
        prepararCandidatoUnico(pago);
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "pending", "100.00", "0.00", null, null));

        service.procesar("order", ORDER_ID);

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq(PAYMENT_ID), eq(ORDER_ID), eq(EstadoPago.PENDIENTE), anyString());
    }

    @Test
    void pagoDeOtroNegocioQuedaFueraDeLaAsociacion() throws Exception {
        Pago correcto = pago(10, null, null);
        Pago ajeno = pago(11, null, null);
        ajeno.setIdNegocio(8L);
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarCandidatosMercadoPagoPorVenta(ID_VENTA))
                .thenReturn(List.of(ajeno, correcto));
        when(ventaRepository.buscarPorId(8L, ID_VENTA)).thenReturn(Optional.empty());
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "ARS"));
        when(orderClient.get(eq(ORDER_ID), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> order("1", "pending", "100.00", "0.00", null, null));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq(PAYMENT_ID), eq(ORDER_ID), eq(EstadoPago.PENDIENTE), anyString());
        verify(pagoRepository, never()).actualizarWebhook(
                eq(11), any(), any(), any(EstadoPago.class), any());
    }

    @Test
    void pagoSinVentaCorrespondienteNoActualiza() throws Exception {
        Pago pago = pago(10, null, null);
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarCandidatosMercadoPagoPorVenta(ID_VENTA))
                .thenReturn(List.of(pago));
        when(ventaRepository.buscarPorId(ID_NEGOCIO, ID_VENTA)).thenReturn(Optional.empty());
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenAnswer(invocacion -> payment("1", "100.00", "ARS"));

        service.procesar("payment", PAYMENT_ID);

        verify(pagoRepository, never()).actualizarWebhook(
                anyInt(), any(), any(), any(EstadoPago.class), any());
        verifyNoInteractions(orderClient);
    }

    private void prepararCandidatoUnico(Pago pago) {
        when(pagoRepository.buscarPorMpPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pagoRepository.buscarCandidatosMercadoPagoPorVenta(ID_VENTA))
                .thenReturn(List.of(pago));
    }

    private WebhookService servicio(Function<Long, ConfiguracionMercadoPago> configuracion) {
        return new WebhookService(
                negocioRepository,
                pagoRepository,
                ventaRepository,
                eventBus,
                new EjecutorDirecto(),
                paymentClient,
                orderClient,
                configuracion);
    }

    private static ConfiguracionMercadoPago configuracion(String token) {
        ConfiguracionMercadoPago configuracion = mock(ConfiguracionMercadoPago.class);
        when(configuracion.obtenerAccessToken()).thenReturn(token);
        return configuracion;
    }

    private static Pago pago(int id, String orderId, String mpPaymentId) {
        Pago pago = new Pago(
                id,
                ID_VENTA,
                TipoDePago.MERCADO_PAGO.name(),
                mpPaymentId,
                null,
                orderId,
                EstadoPago.PENDIENTE,
                new BigDecimal("100.00"),
                "ARS",
                null,
                null,
                "clave",
                LocalDateTime.now(),
                null);
        pago.setIdNegocio(ID_NEGOCIO);
        return pago;
    }

    private static Payment payment(String externalReference, String amount, String currency) {
        Payment payment = mock(Payment.class);
        PaymentOrder paymentOrder = mock(PaymentOrder.class);
        when(paymentOrder.getId()).thenReturn(Long.valueOf(ORDER_ID));
        when(payment.getOrder()).thenReturn(paymentOrder);
        when(payment.getId()).thenReturn(Long.valueOf(PAYMENT_ID));
        when(payment.getExternalReference()).thenReturn(externalReference);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal(amount));
        when(payment.getCurrencyId()).thenReturn(currency);
        return payment;
    }

    private static Order order(String externalReference, String status, String totalAmount,
                               String totalPaid, String paymentId, String paymentStatus) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getExternalReference()).thenReturn(externalReference);
        when(order.getStatus()).thenReturn(status);
        when(order.getTotalAmount()).thenReturn(totalAmount);
        when(order.getTotalPaidAmount()).thenReturn(totalPaid);
        when(order.getCurrency()).thenReturn("ARS");
        if (paymentId != null) {
            OrderPayment pago = mock(OrderPayment.class);
            when(pago.getId()).thenReturn(paymentId);
            when(pago.getStatus()).thenReturn(paymentStatus);
            OrderTransaction transaccion = mock(OrderTransaction.class);
            when(transaccion.getPayments()).thenReturn(List.of(pago));
            when(order.getTransactions()).thenReturn(transaccion);
        }
        return order;
    }

    private static Negocio negocio(long id) {
        return new Negocio(id, "Negocio " + id, "negocio-" + id, null, true, null, null);
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
