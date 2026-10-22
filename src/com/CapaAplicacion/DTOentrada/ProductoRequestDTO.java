package com.CapaAplicacion.DTOentrada;

import com.CapaDominio.Entidades.CategoriaProducto;
import java.math.BigDecimal;

public record ProductoRequestDTO(
    String nombre,
    BigDecimal precio,
    int stock, 
    CategoriaProducto categoria
) {
    public ProductoRequestDTO {
        if (categoria == null) {
            throw new IllegalArgumentException("La categoría del producto es obligatoria.");
        }
        if (categoria == CategoriaProducto.OTROS) {
            throw new IllegalArgumentException("La categoría OTROS es solo de lectura. Especifique una categoría real.");
        }
    }
}