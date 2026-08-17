package com.CapaDominio.Entidades;


import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

public class Carrito {
    private int idCliente;

    private final Map<Integer , ItemCarrito> items ; 

    public Carrito(int idCliente) {
        this.idCliente = idCliente;
        this.items = new HashMap<>();
    }

    public void agregarItem(ItemCarrito nuevoItem) {
        
        if(nuevoItem == null ){ 
                throw new IllegalArgumentException("El ítem no puede ser nulo.");
        }

            int idProducto = nuevoItem.getProducto().getId();

            ItemCarrito itemExistente = items.get(idProducto); 

            if(itemExistente != null) {
                itemExistente.sumarCantidad(nuevoItem.getCantidad());    
            }
            else{
                items.put(idProducto, nuevoItem);
            }

            
        }
        
       
    
    

    public void vaciar() {
    this.items.clear();
}


    public BigDecimal calcularTotalCarrito(IDescuentos descuento) {
        BigDecimal acumuladorTotal = BigDecimal.valueOf(0.0);
        

        for (ItemCarrito item : items.values()) {
         acumuladorTotal = acumuladorTotal.add(item.getSubtotal(descuento));

        }
        
        return acumuladorTotal;

    }
    
    public int getIdCliente() { 
        return idCliente; 
    }

    public Map<Integer, ItemCarrito> getItems() {
        return items;
    }
    
 
}