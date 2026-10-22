package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;
import com.mercadopago.resources.order.OrderPayment;
import com.mercadopago.resources.order.OrderTransaction;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentOrder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookServiceFanoutTest {
    private static final String CLAVE_SECRETO = "mercadopago.webhook-secret";
    private static final String REQUEST_ID = "request-prueba";
    private static final String TIMESTAMP = "1728576000";

    private final String secretoAnterior = System.getProperty(CLAVE_SECRETO);

    @BeforeEach
    void ocultarSecretoGlobal() {
        System.setProperty(CLAVE_SECRETO, " ");
    }

    @AfterEach
    void restaurarSecretoGlobal() {
        restaurarPropiedad(CLAVE_SECRETO, secretoAnterior);
    }

    @Test
    void pagoPersistidoUsaSoloElTokenDelNegocio() throws Exception {
        Pago pago = pago(10, 7L, "1001", "5001");
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorMpPaymentId("5001")).thenReturn(Optional.of(pago));
        when(pagoRepository.buscarPorOrderId("1001")).thenReturn(Optional.of(pago));

        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment payment = payment(1001L);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenReturn(payment);
        OrderClient orderClient = mock(OrderClient.class);
        Order order = order("1001", "pending", null);
        when(orderClient.get(eq("1001"), any(MPRequestOptions.class)))
                .thenReturn(order);
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        ConfiguracionMercadoPago configuracion = configuracion("token-negocio", null);

        WebhookService service = servicio(
                negocioRepository, pagoRepository, paymentClient, orderClient, id -> configuracion);
        service.procesar("payment", "5001");

        ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentClient).get(eq(5001L), opciones.capture());
        assertEquals("token-negocio", opciones.getValue().getAccessToken());
        verify(orderClient).get(
                eq("1001"), argThat(o -> "token-negocio".equals(o.getAccessToken())));
        verify(negocioRepository, never()).listarActivos();
    }

    @Test
    void pagoPersistidoNoHaceFanOutSiFallaLaConsulta() throws Exception {
        Pago pago = pago(10, 7L, "1001", "5001");
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorMpPaymentId("5001")).thenReturn(Optional.of(pago));

        PaymentClient paymentClient = mock(PaymentClient.class);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenThrow(new MPException("fallo temporal"));
        OrderClient orderClient = mock(OrderClient.class);
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        ConfiguracionMercadoPago configuracion = configuracion("token-negocio", null);

        WebhookService service = servicio(
                negocioRepository, pagoRepository, paymentClient, orderClient, id -> configuracion);
        service.procesar("payment", "5001");

        ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentClient).get(eq(5001L), opciones.capture());
        assertEquals("token-negocio", opciones.getValue().getAccessToken());
        verify(negocioRepository, never()).listarActivos();
        verifyNoInteractions(orderClient);
    }

    @Test
    void pagoDesconocidoConTokenGlobalHaceUnaSolaLlamada() throws Exception {
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        when(negocioRepository.listarActivos())
                .thenReturn(List.of(negocio(1), negocio(2), negocio(3)));

        Pago pago = pago(10, 2L, "1001", null);
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorMpPaymentId("5001")).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId("1001")).thenReturn(Optional.of(pago));

        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment payment = payment(1001L);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class)))
                .thenReturn(payment);
        OrderClient orderClient = mock(OrderClient.class);
        Order order = order("1001", "pending", null);
        when(orderClient.get(eq("1001"), any(MPRequestOptions.class)))
                .thenReturn(order);
        ConfiguracionMercadoPago configuracion = configuracion("token-global", null);

        WebhookService service = servicio(
                negocioRepository, pagoRepository, paymentClient, orderClient, id -> configuracion);
        service.procesar("payment", "5001");

        ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentClient).get(eq(5001L), opciones.capture());
        assertEquals("token-global", opciones.getValue().getAccessToken());
        verify(pagoRepository).actualizarWebhook(
                eq(10), eq("5001"), eq("1001"), eq(EstadoPago.PENDIENTE), anyString());
    }

    @Test
    void pagoDesconocidoConTokensDistintosPruebaCadaTokenUnaVez() throws Exception {
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        when(negocioRepository.listarActivos()).thenReturn(List.of(negocio(1), negocio(2)));

        Pago pago = pago(10, 2L, "1001", null);
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorMpPaymentId("5001")).thenReturn(Optional.empty());
        when(pagoRepository.buscarPorOrderId("1001")).thenReturn(Optional.of(pago));

        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment payment = payment(1001L);
        when(paymentClient.get(eq(5001L), any(MPRequestOptions.class))).thenAnswer(invocation -> {
            MPRequestOptions options = invocation.getArgument(1);
            if ("token-1".equals(options.getAccessToken())) {
                throw new MPException("sin acceso");
            }
            return payment;
        });
        OrderClient orderClient = mock(OrderClient.class);
        Order order = order("1001", "pending", null);
        when(orderClient.get(eq("1001"), any(MPRequestOptions.class)))
                .thenReturn(order);
        Map<Long, ConfiguracionMercadoPago> configuraciones = Map.of(
                1L, configuracion("token-1", null),
                2L, configuracion("token-2", null));

        WebhookService service = servicio(
                negocioRepository, pagoRepository, paymentClient, orderClient, configuraciones::get);
        service.procesar("payment", "5001");

        ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentClient, times(2)).get(eq(5001L), opciones.capture());
        assertEquals(
                List.of("token-1", "token-2"),
                opciones.getAllValues().stream().map(MPRequestOptions::getAccessToken).toList());
        verify(orderClient).get(
                eq("1001"), argThat(o -> "token-2".equals(o.getAccessToken())));
    }

    @Test
    void ordenSinPaymentIdPersisteElIdExtraido() throws Exception {
        Pago pago = pago(10, 1L, "order-123", null);
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorOrderId("order-123")).thenReturn(Optional.of(pago));

        PaymentClient paymentClient = mock(PaymentClient.class);
        OrderClient orderClient = mock(OrderClient.class);
        Order order = order("order-123", "pending", "777");
        when(orderClient.get(eq("order-123"), any(MPRequestOptions.class)))
                .thenReturn(order);
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        ConfiguracionMercadoPago configuracion = configuracion("token-negocio", null);

        WebhookService service = servicio(
                negocioRepository, pagoRepository, paymentClient, orderClient, id -> configuracion);
        service.procesar("order", "order-123");

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq("777"), eq("order-123"), eq(EstadoPago.PENDIENTE), anyString());
        verify(negocioRepository, never()).listarActivos();
    }

    @Test
    void ordenPrefierePaymentAprobadoAunqueNoSeaElUltimo() throws Exception {
        Pago pago = pago(10, 1L, "order-123", null);
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorOrderId("order-123")).thenReturn(Optional.of(pago));

        OrderClient orderClient = mock(OrderClient.class);
        Order order = orderConPagos("order-123", "pending",
                orderPayment("111", "pending"),
                orderPayment("222", "approved"),
                orderPayment("333", "rejected"));
        when(orderClient.get(eq("order-123"), any(MPRequestOptions.class)))
                .thenReturn(order);
        ConfiguracionMercadoPago configuracion = configuracion("token-negocio", null);

        WebhookService service = servicio(
                mock(INegocioRepository.class),
                pagoRepository,
                mock(PaymentClient.class),
                orderClient,
                id -> configuracion);
        service.procesar("order", "order-123");

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq("222"), eq("order-123"), eq(EstadoPago.PENDIENTE), anyString());
    }

    @Test
    void ordenSinPaymentAprobadoUsaElUltimo() throws Exception {
        Pago pago = pago(10, 1L, "order-123", null);
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorOrderId("order-123")).thenReturn(Optional.of(pago));

        OrderClient orderClient = mock(OrderClient.class);
        Order order = orderConPagos("order-123", "pending",
                orderPayment("111", "pending"),
                orderPayment("222", "in_process"));
        when(orderClient.get(eq("order-123"), any(MPRequestOptions.class)))
                .thenReturn(order);
        ConfiguracionMercadoPago configuracion = configuracion("token-negocio", null);

        WebhookService service = servicio(
                mock(INegocioRepository.class),
                pagoRepository,
                mock(PaymentClient.class),
                orderClient,
                id -> configuracion);
        service.procesar("order", "order-123");

        verify(pagoRepository).actualizarWebhook(
                eq(10), eq("222"), eq("order-123"), eq(EstadoPago.PENDIENTE), anyString());
    }

    @Test
    void firmaSeAcotaAlNegocioDelPagoPersistido() throws Exception {
        INegocioRepository negocioRepository = mock(INegocioRepository.class);
        when(negocioRepository.listarActivos()).thenReturn(List.of(negocio(1), negocio(2)));

        Pago pagoOrden = pago(10, 1L, "order-123", null);
        Pago pagoPago = pago(11, 2L, "order-456", "5001");
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorOrderId("order-123")).thenReturn(Optional.of(pagoOrden));
        when(pagoRepository.buscarPorMpPaymentId("5001")).thenReturn(Optional.of(pagoPago));
        Map<Long, ConfiguracionMercadoPago> configuraciones = Map.of(
                1L, configuracion(null, "secreto-1"),
                2L, configuracion(null, "secreto-2"));

        WebhookService service = servicio(
                negocioRepository,
                pagoRepository,
                mock(PaymentClient.class),
                mock(OrderClient.class),
                configuraciones::get);

        String firmaOrden1 = crearFirma("secreto-1", "order-123");
        String firmaOrden2 = crearFirma("secreto-2", "order-123");
        String firmaPago1 = crearFirma("secreto-1", "5001");
        String firmaPago2 = crearFirma("secreto-2", "5001");
        String firmaDesconocida = crearFirma("secreto-2", "order-desconocida");

        assertTrue(service.haySecretoFirmaConfigurado("order", "order-123"));
        assertTrue(service.haySecretoFirmaConfigurado("payment", "5001"));
        assertTrue(service.validarFirma("order", "order-123", firmaOrden1, REQUEST_ID));
        assertFalse(service.validarFirma("order", "order-123", firmaOrden2, REQUEST_ID));
        assertTrue(service.validarFirma("order", "order-desconocida", firmaDesconocida, REQUEST_ID));
        assertTrue(service.validarFirma("payment", "5001", firmaPago2, REQUEST_ID));
        assertFalse(service.validarFirma("payment", "5001", firmaPago1, REQUEST_ID));
    }

    private static WebhookService servicio(INegocioRepository negocioRepository,
                                           IPagoRepository pagoRepository,
                                           PaymentClient paymentClient,
                                           OrderClient orderClient,
                                           Function<Long, ConfiguracionMercadoPago> configuracion) {
        return new WebhookService(
                negocioRepository,
                pagoRepository,
                mock(IVentaRepository.class),
                mock(EventBus.class),
                new EjecutorDirecto(),
                paymentClient,
                orderClient,
                configuracion);
    }

    private static ConfiguracionMercadoPago configuracion(String token, String secreto) {
        ConfiguracionMercadoPago configuracion = mock(ConfiguracionMercadoPago.class);
        when(configuracion.obtenerAccessToken()).thenReturn(token);
        when(configuracion.obtenerSecretoWebhook()).thenReturn(secreto);
        return configuracion;
    }

    private static Pago pago(int id, long idNegocio, String orderId, String mpPaymentId) {
        Pago pago = new Pago(
                id,
                1,
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
        pago.setIdNegocio(idNegocio);
        return pago;
    }

    private static Payment payment(Long orderId) {
        Payment payment = mock(Payment.class);
        PaymentOrder paymentOrder = mock(PaymentOrder.class);
        when(paymentOrder.getId()).thenReturn(orderId);
        when(payment.getOrder()).thenReturn(paymentOrder);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("100.00"));
        when(payment.getCurrencyId()).thenReturn("ARS");
        when(payment.getExternalReference()).thenReturn("1");
        return payment;
    }

    private static Order order(String orderId, String estado, String paymentId) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getStatus()).thenReturn(estado);
        when(order.getExternalReference()).thenReturn("1");
        when(order.getTotalAmount()).thenReturn("100.00");
        when(order.getCurrency()).thenReturn("ARS");
        if (paymentId != null) {
            OrderPayment pago = mock(OrderPayment.class);
            when(pago.getId()).thenReturn(paymentId);
            when(pago.getStatus()).thenReturn("approved");
            OrderTransaction transaccion = mock(OrderTransaction.class);
            when(transaccion.getPayments()).thenReturn(List.of(pago));
            when(order.getTransactions()).thenReturn(transaccion);
        }
        return order;
    }

    private static Order orderConPagos(String orderId, String estado, OrderPayment... pagos) {
        Order order = order(orderId, estado, null);
        OrderTransaction transaccion = mock(OrderTransaction.class);
        when(transaccion.getPayments()).thenReturn(List.of(pagos));
        when(order.getTransactions()).thenReturn(transaccion);
        return order;
    }

    private static OrderPayment orderPayment(String id, String status) {
        OrderPayment pago = mock(OrderPayment.class);
        when(pago.getId()).thenReturn(id);
        when(pago.getStatus()).thenReturn(status);
        return pago;
    }

    private static Negocio negocio(long id) {
        return new Negocio(id, "Negocio " + id, "negocio-" + id, null, true, null, null);
    }

    private static String crearFirma(String secreto, String id) throws Exception {
        String manifiesto = "id:" + id + ";request-id:" + REQUEST_ID + ";ts:" + TIMESTAMP + ";";
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(hmac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        return "ts=" + TIMESTAMP + ",v1=" + hash;
    }

    private static void restaurarPropiedad(String clave, String valor) {
        if (valor == null) {
            System.clearProperty(clave);
        } else {
            System.setProperty(clave, valor);
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
