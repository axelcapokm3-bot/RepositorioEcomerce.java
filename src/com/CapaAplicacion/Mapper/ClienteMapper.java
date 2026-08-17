package com.CapaAplicacion.Mapper;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaDominio.Entidades.Cliente;

public class ClienteMapper {

    /**
     * Convierte un DTO de entrada a una entidad Cliente.
     */
    public static Cliente toEntidad(int nuevoId, ClienteRequestDTO dto) {
        return new Cliente(
            nuevoId,
            dto.dni(),
            dto.nombre(),
            dto.apellido(),
            dto.direccion(),
            dto.fechaNacimiento(),
            dto.localidad()
        );
    }

    /**
     * Convierte una entidad Cliente a un DTO de salida.
     */
    public static ClienteResponseDTO toResponseDTO(Cliente cliente) {
        return new ClienteResponseDTO(
            cliente.getId(),
            cliente.getDni(),
            cliente.getCuit(),
            cliente.getNombre() + " " + cliente.getApellido(),
            cliente.getDireccion(),
            cliente.getFechaNacimiento(),
            cliente.getLocalidad(),
            cliente.isActivo()
        );
    }
}
