package com.CapaDominio.Entidades;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Representa un intento de cobro asociado a una venta. */
public class Pago {
    private Integer id;
    private int idVenta;
    private String pasarela;
    private String mpPaymentId;
    private String orderId;
    private String preferenciaId;
    private EstadoPago estado;
    private BigDecimal monto;
    private String moneda = "ARS";
    private String urlPago;
    private String rawResponse;
    private String claveIdempotenciaPasarela;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private long idNegocio;

    public Pago() {}

    public Pago(Integer id, int idVenta, String pasarela, String mpPaymentId, String preferenciaId,
                String orderId, EstadoPago estado, BigDecimal monto, String moneda,
                String urlPago, String rawResponse,
                String claveIdempotenciaPasarela, LocalDateTime fechaCreacion,
                LocalDateTime fechaActualizacion) {
        this.id = id;
        this.idVenta = idVenta;
        this.pasarela = pasarela;
        this.mpPaymentId = mpPaymentId;
        this.orderId = orderId;
        this.preferenciaId = preferenciaId;
        this.estado = estado;
        this.monto = monto;
        this.moneda = moneda == null ? "ARS" : moneda;
        this.urlPago = urlPago;
        this.rawResponse = rawResponse;
        this.claveIdempotenciaPasarela = claveIdempotenciaPasarela;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(int idVenta) {
        this.idVenta = idVenta;
    }

    public String getPasarela() {
        return pasarela;
    }

    public void setPasarela(String pasarela) {
        this.pasarela = pasarela;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public void setMpPaymentId(String mpPaymentId) {
        this.mpPaymentId = mpPaymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPreferenciaId() {
        return preferenciaId;
    }

    public void setPreferenciaId(String preferenciaId) {
        this.preferenciaId = preferenciaId;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public void setEstado(EstadoPago estado) {
        this.estado = estado;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda == null ? "ARS" : moneda;
    }

    public String getUrlPago() {
        return urlPago;
    }

    public void setUrlPago(String urlPago) {
        this.urlPago = urlPago;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getClaveIdempotenciaPasarela() {
        return claveIdempotenciaPasarela;
    }

    public void setClaveIdempotenciaPasarela(String claveIdempotenciaPasarela) {
        this.claveIdempotenciaPasarela = claveIdempotenciaPasarela;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public long getIdNegocio() {
        return idNegocio;
    }

    public void setIdNegocio(long idNegocio) {
        this.idNegocio = idNegocio;
    }
}
