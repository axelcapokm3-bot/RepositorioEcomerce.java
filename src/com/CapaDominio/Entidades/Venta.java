package com.CapaDominio.Entidades;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Venta {
    private int id;
    private int idCliente;
    private LocalDateTime fechaCompra;
    private EstadoVenta estadoCompra; 
    private TipoDePago tipoPago;
    private List<DetalleVenta> detalles;
    private String claveIdempotenciaSolicitud;

    public Venta(int idCliente, TipoDePago tipoPago) {
        this(idCliente, tipoPago, null);
    }

    public Venta(int idCliente, TipoDePago tipoPago, String claveIdempotenciaSolicitud) {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID del cliente debe ser válido.");
        }
        if (tipoPago == null) {
            throw new IllegalArgumentException("Debe especificar un tipo de pago.");
        }
        if (claveIdempotenciaSolicitud != null && claveIdempotenciaSolicitud.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia no puede estar vacía.");
        }

        this.idCliente = idCliente;
        this.detalles = new ArrayList<>();
        this.fechaCompra = LocalDateTime.now(); 
        this.estadoCompra = EstadoVenta.PENDIENTE;  
        this.tipoPago = tipoPago;
        this.claveIdempotenciaSolicitud = claveIdempotenciaSolicitud;
    }

 
    public void cargarDetallesDesdeCarrito(Carrito carrito, IDescuentos descuento) {
        if (this.estadoCompra != EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("No se pueden modificar los detalles de una venta que no esté PENDIENTE.");
        }
        if (carrito == null || carrito.getItems().isEmpty()) {
            throw new IllegalArgumentException("El carrito no puede estar vacío para generar una venta.");
        }

        this.detalles.clear(); 


        for (ItemCarrito item : carrito.getItems().values()) {
 BigDecimal subtotalConDescuento = item.getSubtotal(descuento);


BigDecimal cantidadBD = BigDecimal.valueOf(item.getCantidad());

BigDecimal precioUnitarioEfectivo = subtotalConDescuento.divide(cantidadBD, 2, java.math.RoundingMode.HALF_UP);
 
this.detalles.add(new DetalleVenta(
    item.getProducto().getId(),
    precioUnitarioEfectivo, 
    item.getCantidad()
));
        }
    }


    public List<DetalleVenta> getDetalles() {
        return detalles;
    }

    public BigDecimal calculoTotal() { 
     BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta d : detalles) {
            total = total.add(d.getSubtotal());
        }
       return total;
    }

    public int getId() {
        return id;
    }



    public int getIdCliente() {
        return idCliente;
    }

    public LocalDateTime getFechaCompra() {
        return fechaCompra;
    }

    public EstadoVenta getEstadoCompra() {
        return estadoCompra;
    }

    

    public TipoDePago getTipoPago() {
        return tipoPago;
    }

    public String getClaveIdempotenciaSolicitud() {
        return claveIdempotenciaSolicitud;
    }

  private void cambiarEstado(EstadoVenta nuevoEstado) {
    if (nuevoEstado == null) {
        throw new IllegalArgumentException("El nuevo estado no puede ser nulo.");
    }


    if (!this.estadoCompra.puedeTransicionarA(nuevoEstado)) {
        throw new IllegalStateException(
            "Transición inválida: no se puede pasar de " + this.estadoCompra + " a " + nuevoEstado + "."
        );
    }

  
    this.estadoCompra = nuevoEstado;
}

public void setId(int id) {

    if (this.id != 0) {
        throw new IllegalStateException("El ID de la venta ya ha sido asignado y no puede modificarse.");
    }
    this.id = id;
}




    public void Pagar(){
            cambiarEstado(EstadoVenta.PAGADO);
    }

        public void Cancelar(){
             cambiarEstado(EstadoVenta.CANCELADO);
    }

    public void Enviar(){
            cambiarEstado(EstadoVenta.ENVIADO);
    }




}