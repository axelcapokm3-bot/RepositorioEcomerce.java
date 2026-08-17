package com.CapaAplicacion.Mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.CapaAplicacion.DTOentrada.CuentaRequestDTO;
import com.CapaAplicacion.DTOsalida.CuentaResponseDTO;
import com.CapaDominio.Entidades.Cuenta;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuentaMapperTest {

    private CuentaRequestDTO dtoValido() {
        return new CuentaRequestDTO(
                "jperez", "jperez@gmail.com", "Password123", 10, "CLIENTE", new BigDecimal("500"));
    }

    @Test
    @DisplayName("Convierte DTO de entrada a entidad")
    void deRegistroDtoAEntidad() {
        Cuenta cuenta = CuentaMapper.deRegistroDtoAEntidad(3, dtoValido());
        assertEquals(3, cuenta.getId());
        assertEquals("jperez", cuenta.getNombreCuenta());
        assertEquals("Password123", cuenta.getContrasenia());
        assertEquals(new BigDecimal("500"), cuenta.getSaldo());
    }

    @Test
    @DisplayName("Convierte entidad a DTO de salida")
    void deEntidadAResponseDto() {
        Cuenta cuenta = new Cuenta(3, "jperez", "jperez@gmail.com", "Password123", "CLIENTE", 10, new BigDecimal("500"));
        cuenta.setToken("token123");
        CuentaResponseDTO dto = CuentaMapper.deEntidadAResponseDto(cuenta);
        assertEquals(3, dto.id());
        assertEquals("jperez@gmail.com", dto.correoElectronico());
        assertEquals("token123", dto.token());
        assertEquals(new BigDecimal("500"), dto.saldo());
    }
}