package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class Producto { 
    private int id;
    private String nombre;
    private BigDecimal precio;
    private int stockFisico ;
    private int stockReservado ; 
    private CategoriaProducto categoria  ;

    public Producto(String nombre, BigDecimal precio, int stockFisico,int stockReservado, int id ,CategoriaProducto categoria    ) {

        if(categoria == null )  {
              throw new IllegalArgumentException("La categoria del producto no puede estar vacío.");
        }

        if (nombre == null || nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        if (id < 0) {
            throw new IllegalArgumentException("El id del producto no puede ser negativo.");
        }
  
        validarPrecioMayorACero(precio);
        
        if (stockFisico < 0) {
            throw new IllegalArgumentException("El stock del producto no puede ser negativo.");
        }

          if (stockReservado < 0) {
            throw new IllegalArgumentException("El stock del producto no puede ser negativo.");
        }

        this.nombre = nombre;
        this.precio = precio;
        this.stockFisico = stockFisico;
         this.stockReservado =  0 ;
        this.id = id;
        this.categoria = categoria  ;
    }



    public int getId() {
        return id;
    }

    public void setId(int idNuevo){
        if(id != 0){
            throw new IllegalArgumentException("Ya existe");
        }

        this.id = idNuevo ;
    }
 
    public int getStockReservado() { 
        return stockReservado ; 
    }
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        this.nombre = nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        validarPrecioMayorACero(precio);
        this.precio = precio;
    }

    public CategoriaProducto getCategoria() {
        return categoria;
    }
    public int getStockFisico() {
        return stockFisico;
    }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        this.stockFisico = stock;
    }

    public int getStockDisponible() { 
        return this.stockFisico  - this.stockReservado ; 
    }



    public void reservarStock(int cantidad)  {
        validarCantidadPositiva(cantidad) ; 
        validarDisponibilidad(cantidad) ; 
        this.stockReservado += cantidad ; 
    }

    public void liberarReserva(int cantidad) { 
        validarCantidadPositiva(cantidad)  ; 
            if(cantidad  > this.stockReservado) { 
                throw new IllegalArgumentException("No se puede liberar más stock del reservado actualmente."); 
            }
            this.stockReservado -= cantidad ; 
    }





   
    public synchronized void  reducirStock(int cantidad) { 
        validarCantidadPositiva(cantidad);
        validarDisponibilidad(cantidad);

                    if (this.stockReservado >= cantidad) {
              
                        this.stockReservado -= cantidad;
                    } 
                        this.stockFisico -= cantidad;    
                  if (this.stockFisico == 0 ) {
                      reponerStock(); 
                  }
                }

    public void reponerStock() {

    EstadoStock estadoActual = EstadoStock.evaluarEstado(this.stockFisico);

    int cantidad = estadoActual.getCantidadAReponer();

    if (cantidad > 0) {
        this.stockFisico += cantidad;
       
    }
}


    public void actualizarPrecio(BigDecimal nuevoPrecio) {
        validarPrecioMayorACero(nuevoPrecio);
        this.precio = nuevoPrecio;
    }


    public void validarCantidadPositiva(int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
    }

    public void validarDisponibilidad(int cantidad) {
        if (cantidad > this.stockFisico) throw new IllegalArgumentException("Stock insuficiente.");
    }

   public void validarPrecioMayorACero(BigDecimal precio) {
    if (precio == null) {
        throw new IllegalArgumentException("El precio no puede ser nulo.");
    }
    if (precio.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("El precio debe ser mayor a cero.");
    }
    }
    

public void reintegrarStock(int cantidad) {
    if (cantidad <= 0) {
        throw new IllegalArgumentException("La cantidad a reintegrar debe ser mayor a cero.");
    }
    this.stockFisico += cantidad;
}

}
        
    




