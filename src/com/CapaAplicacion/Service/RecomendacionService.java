package com.CapaAplicacion.Service;

import com.CapaDominio.Entidades.Producto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecomendacionService {

    private static final BigDecimal MARGEN_FLEXIBLE = BigDecimal.valueOf(0.05); 

    public List<Producto> obtenerRecomendados(Producto productoActual, List<Producto> todosLosProductos, BigDecimal precioMin, BigDecimal precioMax) {
        
        List<Producto> ideales = new ArrayList<>();
        List<Producto> flexibles = new ArrayList<>();

        
        BigDecimal factorMargen = BigDecimal.ONE.add(MARGEN_FLEXIBLE);
        BigDecimal limiteSuperior = precioMax.multiply(factorMargen);

        for (Producto p : todosLosProductos) {

            
            if (p.getStockDisponible() == 0 || p.getId() == productoActual.getId()) {
                continue;
            }

          
            if (Objects.equals(p.getCategoria(), productoActual.getCategoria())) {

                BigDecimal precioProducto = p.getPrecio();

              
                if (precioProducto.compareTo(precioMin) >= 0 && precioProducto.compareTo(precioMax) <= 0) {
                    ideales.add(p);
                } 
            
                else if (precioProducto.compareTo(precioMax) > 0 && precioProducto.compareTo(limiteSuperior) <= 0) {
                    flexibles.add(p);
                }
            }
        }


        List<Producto> resultadoFinal = new ArrayList<>();
        resultadoFinal.addAll(ideales);
        resultadoFinal.addAll(flexibles);

        return resultadoFinal;
    }
}