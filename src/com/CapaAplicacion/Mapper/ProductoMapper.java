package com.CapaAplicacion.Mapper;

import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaDominio.Entidades.EstadoStock;
import com.CapaDominio.Entidades.Producto;

public class ProductoMapper {

    public static ProductoResponseDTO toResponseDTO(Producto p) {
        return new ProductoResponseDTO(
          p.getId(),
            p.getNombre(),
            p.getPrecio(),
            p.getStockFisico(),
            p.getStockReservado(),
            p.getStockDisponible(),
            EstadoStock.evaluarEstado(p.getStockFisico()), // Mapea el Enum
            p.getCategoria()
        );
    }
}