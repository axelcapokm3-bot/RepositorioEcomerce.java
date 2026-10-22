package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaDominio.Excepciones.TransicionPagoInvalidaException;
import com.CapaInfraestructura.Implementacion.PasarelaFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class PagoService {
    private static final Logger LOGGER = Logger.getLogger(PagoService.class.getName());

    private final IPagoRepository pagoRepository;
    private final IVentaRepository ventaRepository;
    private final EventBus eventBus;
    private final ITransaccion transaccion;

    public PagoService(IPagoRepository pagoRepository, IVentaRepository ventaRepository,
                       EventBus eventBus) {
        this(pagoRepository, ventaRepository, eventBus, null);
    }

    public PagoService(IPagoRepository pagoRepository, IVentaRepository ventaRepository,
                       EventBus eventBus, ITransaccion transaccion) {
        if (pagoRepository == null || ventaRepository == null || eventBus == null) {
            throw new IllegalArgumentException("Los repositorios de pago/venta y el bus de eventos son obligatorios.");
        }
        this.pagoRepository = pagoRepository;
        this.ventaRepository = ventaRepository;
        this.eventBus = eventBus;
        this.transaccion = transaccion;
    }

    public ResultadoPago iniciarCheckout(Venta venta) {
        validarVentaOnline(venta);

        String claveIdempotenciaPasarela = crearClaveIdempotenciaPasarela(venta);
        Optional<Pago> pagoExistente =
                pagoRepository.buscarPorClaveIdempotenciaPasarela(venta.getIdNegocio(), claveIdempotenciaPasarela);
        Pago pago;
        if (pagoExistente.isPresent()) {
            pago = pagoExistente.get();
            validarMismoPago(pago, venta);
            validarVentaPendiente(venta);
            if (tieneOperacionExterna(pago)) {
                return crearResultado(pago);
            }
        } else {
            validarVentaPendiente(venta);
            pago = crearPagoPendiente(venta, claveIdempotenciaPasarela);
            pagoRepository.insertar(pago);
            pago = pagoRepository.buscarPorClaveIdempotenciaPasarela(venta.getIdNegocio(), claveIdempotenciaPasarela)
                    .orElse(pago);
            validarMismoPago(pago, venta);
            if (tieneOperacionExterna(pago)) {
                return crearResultado(pago);
            }
        }

        if (pago.getId() == null) {
            throw new IllegalStateException("No se pudo persistir el intento de pago antes del checkout.");
        }

        ResultadoPago resultado = PasarelaFactory.obtener(venta.getIdNegocio(), venta.getTipoPago())
                .procesar(venta, claveIdempotenciaPasarela);
        String orderId = esMercadoPago(venta) ? resultado.paymentId() : null;
        String externalId = esStripe(venta) ? resultado.paymentId() : null;
        pagoRepository.actualizarCheckout(
                pago.getId(),
                externalId,
                orderId,
                resultado.urlPago(),
                EstadoPago.PENDIENTE,
                resultado.rawResponse());
        return resultado;
    }

    public void validarConfiguracionCheckout(long idNegocio, TipoDePago tipoDePago) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio del checkout no es válido.");
        }
        if (tipoDePago != TipoDePago.MERCADO_PAGO && tipoDePago != TipoDePago.STRIPE) {
            throw new IllegalArgumentException(
                    "El checkout público solo está disponible para Mercado Pago y Stripe.");
        }
        PasarelaFactory.obtener(idNegocio, tipoDePago).validarConfiguracion();
    }

    public void aplicarEstadoDePasarela(Pago pago, EstadoPago nuevoEstado,
                                        String mpPaymentId, String orderId,
                                        String rawResponse) {
        if (pago == null || pago.getId() == null || nuevoEstado == null) {
            throw new IllegalArgumentException("El pago y su estado son obligatorios.");
        }
        String mpPaymentIdPersistido = conservarIdentificador(pago.getMpPaymentId(), mpPaymentId);
        String orderIdPersistido = conservarIdentificador(pago.getOrderId(), orderId);
        if (transaccion != null) {
            aplicarEstadoDePasarelaTransaccional(
                    pago, nuevoEstado, mpPaymentIdPersistido, orderIdPersistido, rawResponse);
            return;
        }

        boolean cambioEstado = pago.getEstado() != nuevoEstado;
        boolean cambioIdentificadores = !Objects.equals(mpPaymentIdPersistido, pago.getMpPaymentId())
                || !Objects.equals(orderIdPersistido, pago.getOrderId());
        if (cambioEstado && (pago.getEstado() == null
                || !pago.getEstado().puedeTransicionarA(nuevoEstado))) {
            throw new TransicionPagoInvalidaException(
                    "Transición de pago inválida: no se puede pasar de "
                            + pago.getEstado() + " a " + nuevoEstado + ".");
        }
        if (cambioEstado || cambioIdentificadores) {
            pagoRepository.actualizarWebhook(
                    pago.getId(), mpPaymentIdPersistido, orderIdPersistido, nuevoEstado, rawResponse);
        }
        if (cambioIdentificadores) {
            pago.setMpPaymentId(mpPaymentIdPersistido);
            pago.setOrderId(orderIdPersistido);
        }
        pago.transicionarA(nuevoEstado);
        if (nuevoEstado != EstadoPago.APROBADO) {
            return;
        }

        marcarVentaComoPagada(pago.getIdNegocio(), pago.getIdVenta());
    }

    private void aplicarEstadoDePasarelaTransaccional(Pago pago, EstadoPago nuevoEstado,
                                                      String mpPaymentId, String orderId,
                                                      String rawResponse) {
        Pago[] pagoBloqueadoFinal = {null};
        Venta[] ventaPagada = {null};
        transaccion.ejecutarTransaccion(connection -> {
            Venta venta = ventaRepository
                    .buscarPorIdParaUpdate(pago.getIdNegocio(), pago.getIdVenta(), connection)
                    .orElseThrow(() -> new IllegalStateException(
                            "No existe la venta asociada al pago " + pago.getId() + "."));
            Pago pagoBloqueado = pagoRepository.buscarPorIdParaUpdate(pago.getId(), connection)
                    .orElseThrow(() -> new IllegalStateException(
                            "No existe el pago " + pago.getId() + " para actualizar."));
            if (pagoBloqueado.getIdVenta() != pago.getIdVenta()
                    || pagoBloqueado.getIdNegocio() != pago.getIdNegocio()) {
                throw new IllegalStateException(
                        "El pago no pertenece a la venta o al negocio indicado.");
            }

            boolean cambioEstado = pagoBloqueado.getEstado() != nuevoEstado;
            boolean cambioIdentificadores = !Objects.equals(mpPaymentId, pagoBloqueado.getMpPaymentId())
                    || !Objects.equals(orderId, pagoBloqueado.getOrderId());
            pagoBloqueado.transicionarA(nuevoEstado);

            if (nuevoEstado == EstadoPago.APROBADO) {
                if (venta.getEstadoCompra() == EstadoVenta.CANCELADO
                        || venta.getEstadoCompra() == EstadoVenta.EXPIRADO) {
                    LOGGER.warning("Pago aprobado tardío para una venta "
                            + venta.getEstadoCompra() + "; se registra el pago para "
                            + "conciliación sin reactivar la venta. Venta " + venta.getId()
                            + ", pago " + pagoBloqueado.getId() + ".");
                } else if (venta.getEstadoCompra() == EstadoVenta.PENDIENTE) {
                    venta.Pagar();
                    ventaRepository.actualizar(
                            venta.getIdNegocio(), venta.getId(), venta, connection);
                    ventaPagada[0] = venta;
                }
            }

            if (cambioEstado || cambioIdentificadores) {
                pagoRepository.actualizarWebhook(
                        pagoBloqueado.getId(), mpPaymentId, orderId, nuevoEstado,
                        rawResponse, connection);
            }
            pagoBloqueado.setMpPaymentId(mpPaymentId);
            pagoBloqueado.setOrderId(orderId);
            pagoBloqueadoFinal[0] = pagoBloqueado;
        });

        if (pagoBloqueadoFinal[0] != null) {
            pago.setMpPaymentId(pagoBloqueadoFinal[0].getMpPaymentId());
            pago.setOrderId(pagoBloqueadoFinal[0].getOrderId());
            pago.setEstado(pagoBloqueadoFinal[0].getEstado());
        }
        if (ventaPagada[0] != null) {
            eventBus.publicar(new VentasEventos("PAGADA", ventaPagada[0]));
        }
    }

    private String conservarIdentificador(String actual, String nuevo) {
        return nuevo == null || nuevo.isBlank() ? actual : nuevo;
    }

    public void confirmarPagoManual(long idNegocio, int idVenta) {
        if (transaccion == null) {
            confirmarPagoManualSinTransaccion(idNegocio, idVenta);
            return;
        }

        Venta[] ventaPagada = {null};
        transaccion.ejecutarTransaccion(connection -> {
            Venta venta = ventaRepository.buscarPorIdParaUpdate(idNegocio, idVenta, connection)
                    .orElseThrow(() -> new IllegalArgumentException("No existe la venta solicitada."));
            validarPagoManual(venta);
            Pago pagoPendiente = pagoRepository
                    .buscarPendientePorVentaParaUpdate(idNegocio, idVenta, connection)
                    .orElseThrow(() -> new IllegalStateException(
                            "La venta no tiene un pago pendiente para confirmar."));
            pagoPendiente.transicionarA(EstadoPago.APROBADO);
            pagoRepository.actualizarEstado(
                    pagoPendiente.getId(), EstadoPago.APROBADO,
                    pagoPendiente.getRawResponse(), connection);
            venta.Pagar();
            ventaRepository.actualizar(idNegocio, idVenta, venta, connection);
            ventaPagada[0] = venta;
        });
        eventBus.publicar(new VentasEventos("PAGADA", ventaPagada[0]));
    }

    private void confirmarPagoManualSinTransaccion(long idNegocio, int idVenta) {
        Venta venta = buscarVenta(idNegocio, idVenta);
        validarPagoManual(venta);
        Pago pagoPendiente = buscarPagoPendiente(idNegocio, idVenta);
        pagoPendiente.transicionarA(EstadoPago.APROBADO);
        pagoRepository.actualizarEstado(
                pagoPendiente.getId(),
                EstadoPago.APROBADO,
                pagoPendiente.getRawResponse());
        marcarVentaComoPagada(idNegocio, idVenta);
    }

    private void validarPagoManual(Venta venta) {
        if (venta.getTipoPago() != TipoDePago.EFECTIVO
                && venta.getTipoPago() != TipoDePago.TRANSFERENCIA) {
            throw new IllegalArgumentException(
                    "La confirmación manual solo se permite para efectivo o transferencia.");
        }
        if (venta.getEstadoCompra() != EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("La venta no está pendiente de pago.");
        }
    }

    private void validarVentaOnline(Venta venta) {
        if (venta == null || venta.getId() <= 0) {
            throw new IllegalArgumentException("La venta debe estar guardada antes de iniciar el checkout.");
        }
        if (venta.getIdNegocio() <= 0) {
            throw new IllegalArgumentException("La venta debe pertenecer a un negocio válido.");
        }
        if (!esMercadoPago(venta) && !esStripe(venta)) {
            throw new IllegalArgumentException(
                    "El checkout público solo está disponible para Mercado Pago y Stripe.");
        }
    }

    private void validarVentaPendiente(Venta venta) {
        if (venta.getEstadoCompra() != EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("Solo se puede iniciar el pago de una venta pendiente.");
        }
    }

    private Pago crearPagoPendiente(Venta venta, String claveIdempotenciaPasarela) {
        Pago pago = new Pago(
                null,
                venta.getId(),
                venta.getTipoPago().name(),
                null,
                null,
                null,
                EstadoPago.PENDIENTE,
                venta.calculoTotal(),
                "ARS",
                null,
                null,
                claveIdempotenciaPasarela,
                LocalDateTime.now(),
                null);
        pago.setIdNegocio(venta.getIdNegocio());
        return pago;
    }

    private String crearClaveIdempotenciaPasarela(Venta venta) {
        return "venta-" + venta.getId() + "-"
                + venta.getTipoPago().name().toLowerCase(Locale.ROOT);
    }

    private void validarMismoPago(Pago pago, Venta venta) {
        if (pago.getIdVenta() != venta.getId()
                || pago.getIdNegocio() != venta.getIdNegocio()
                || !pago.getPasarela().equals(venta.getTipoPago().name())
                || pago.getMonto().compareTo(venta.calculoTotal()) != 0) {
            throw new IllegalStateException(
                    "La clave de idempotencia de la pasarela ya está asociada a otro pago.");
        }
    }

    private boolean tieneOperacionExterna(Pago pago) {
        if (esMercadoPago(pago)) {
            return pago.getOrderId() != null && !pago.getOrderId().isBlank();
        }
        return pago.getMpPaymentId() != null && !pago.getMpPaymentId().isBlank();
    }

    private boolean esMercadoPago(Venta venta) {
        return venta.getTipoPago() == TipoDePago.MERCADO_PAGO;
    }

    private boolean esStripe(Venta venta) {
        return venta.getTipoPago() == TipoDePago.STRIPE;
    }

    private boolean esMercadoPago(Pago pago) {
        return TipoDePago.MERCADO_PAGO.name().equals(pago.getPasarela());
    }

    private ResultadoPago crearResultado(Pago pago) {
        String identificadorExterno = esMercadoPago(pago) ? pago.getOrderId() : pago.getMpPaymentId();
        String mensaje = crearMensaje(pago.getEstado());
        return new ResultadoPago(
                pago.getEstado() == EstadoPago.APROBADO,
                String.valueOf(pago.getIdVenta()),
                mensaje,
                pago.getMonto(),
                pago.getFechaActualizacion() == null
                        ? pago.getFechaCreacion()
                        : pago.getFechaActualizacion(),
                pago.getPasarela(),
                pago.getUrlPago(),
                pago.getEstado(),
                identificadorExterno,
                pago.getRawResponse());
    }

    private String crearMensaje(EstadoPago estado) {
        if (estado == EstadoPago.APROBADO) {
            return "Pago aprobado.";
        }
        if (estado == EstadoPago.RECHAZADO) {
            return "El proveedor rechazó el pago.";
        }
        if (estado == EstadoPago.CANCELADO) {
            return "El pago fue cancelado.";
        }
        return "El pago está pendiente de confirmación.";
    }

    private Venta buscarVenta(long idNegocio, int idVenta) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio debe ser positivo.");
        }
        if (idVenta <= 0) {
            throw new IllegalArgumentException("El ID de venta debe ser positivo.");
        }
        Optional<Venta> venta = ventaRepository.buscarPorId(idNegocio, idVenta);
        if (venta.isEmpty()) {
            throw new IllegalArgumentException("No existe la venta solicitada.");
        }
        return venta.get();
    }

    private Pago buscarPagoPendiente(long idNegocio, int idVenta) {
        List<Pago> pagos = pagoRepository.buscarPorVenta(idNegocio, idVenta);
        for (Pago pago : pagos) {
            if (pago.getEstado() == EstadoPago.PENDIENTE) {
                return pago;
            }
        }
        throw new IllegalStateException("La venta no tiene un pago pendiente para confirmar.");
    }

    private void marcarVentaComoPagada(long idNegocio, int idVenta) {
        Venta venta = buscarVenta(idNegocio, idVenta);
        if (venta.getEstadoCompra() == EstadoVenta.CANCELADO
                || venta.getEstadoCompra() == EstadoVenta.EXPIRADO) {
            LOGGER.warning("Pago aprobado tardío para una venta "
                    + venta.getEstadoCompra() + "; se conserva el pago para conciliación. "
                    + "Venta " + idVenta + ".");
            return;
        }
        if (venta.getEstadoCompra() != EstadoVenta.PENDIENTE) {
            return;
        }
        venta.Pagar();
        if (ventaRepository.marcarComoPagada(idNegocio, idVenta)) {
            eventBus.publicar(new VentasEventos("PAGADA", venta));
        }
    }
}
