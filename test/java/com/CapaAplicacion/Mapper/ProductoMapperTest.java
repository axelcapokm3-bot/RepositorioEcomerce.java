package com.CapaAplicacion.Mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.CapaAplicacion.DTOsalida.ProductoResponseDTO;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.EstadoStock;
import com.CapaDominio.Entidades.Producto;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductoMapperTest {

    @Test
    @DisplayName("Mapea entidad a DTO con estado de stock")
    void toResponseDTO() {
        Producto p = new Producto("Mouse", new BigDecimal("50"), 5, 0, 3, CategoriaProducto.ELECTRONICA);
        ProductoResponseDTO dto = ProductoMapper.toResponseDTO(p);
        assertEquals(3, dto.id());
        assertEquals("Mouse", dto.nombre());
        assertEquals(new BigDecimal("50"), dto.precio());
        assertEquals(5, dto.stockFisico());
        assertEquals(0, dto.stockReservado());
        assertEquals(5, dto.stockDisponible());
        assertEquals(EstadoStock.CRITICO, dto.estadoStock());
        assertEquals(CategoriaProducto.ELECTRONICA, dto.categoria());
    }
}