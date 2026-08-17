package com.CapaAplicacion.DTOsalida;
import com.CapaDominio.Entidades.*;
import java.math.BigDecimal;

public record ProductoResponseDTO(

    int id,
    String nombre,
    BigDecimal precio,
    int stockFisico,
    int stockReservado,
    int stockDisponible, 
    EstadoStock estadoStock,
    CategoriaProducto categoria
) {}