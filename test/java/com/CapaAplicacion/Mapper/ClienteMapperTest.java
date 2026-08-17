package com.CapaAplicacion.Mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaDominio.Entidades.Cliente;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteMapperTest {

    private ClienteRequestDTO dtoValido() {
        return new ClienteRequestDTO(
                "34567890", "20345678909", "Juan", "Perez",
                "Av. Siempre 742", LocalDate.now().minusYears(30), "Springfield");
    }

    @Test
    @DisplayName("Convierte DTO de entrada a entidad")
    void toEntidad() {
        Cliente cliente = ClienteMapper.toEntidad(5, dtoValido());
        assertEquals(5, cliente.getId());
        assertEquals("34567890", cliente.getDni());
        assertEquals("Juan", cliente.getNombre());
        assertTrue(cliente.isActivo());
    }

    @Test
    @DisplayName("Convierte entidad a DTO de salida")
    void toResponseDTO() {
        Cliente cliente = new Cliente(5, "34567890", "Juan", "Perez",
                "Av. Siempre 742", LocalDate.now().minusYears(30), "Springfield");
        cliente.asignarElCuit("20345678909");
        ClienteResponseDTO dto = ClienteMapper.toResponseDTO(cliente);
        assertEquals(5, dto.id());
        assertEquals("Juan Perez", dto.nombreCompleto());
        assertEquals("20345678909", dto.cuit());
        assertTrue(dto.activo());
    }
}