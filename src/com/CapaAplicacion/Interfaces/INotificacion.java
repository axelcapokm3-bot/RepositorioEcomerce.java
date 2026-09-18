package com.CapaAplicacion.Interfaces;
import com.CapaDominio.Entidades.Venta ; 
public interface INotificacion {
    public void enviarConfirmacionVenta(Venta venta) ; 
    public void enviarNotificacionPago(Venta venta) ;
    public void enviarAlertaStockBajo(int idProducto , String nombreProducto); 

    
} 
