package com.CapaAplicacion.DTOentrada;

import com.CapaDominio.Entidades.CategoriaProducto;
import java.math.BigDecimal;

public record ProductoRequestDTO(
    String nombre,
    BigDecimal precio,
    int stock, 
    CategoriaProducto categoria
) {}