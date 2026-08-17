package com.CapaAplicacion.Mapper;

import com.CapaAplicacion.DTOentrada.*;
import com.CapaAplicacion.DTOsalida.CuentaResponseDTO;
import com.CapaDominio.Entidades.Cuenta;

public class CuentaMapper {

    public static Cuenta deRegistroDtoAEntidad(int nuevoId, CuentaRequestDTO dto) {
        return new Cuenta(
            nuevoId,
            dto.nombreCuenta(),      // Sin el prefijo "get"
            dto.correoElectronico(),
            dto.contraseniaPlana(),
            dto.rolUsuario(),
            dto.idCliente(),
            dto.saldo()
        );
    }

    public static CuentaResponseDTO deEntidadAResponseDto(Cuenta cuenta) {
        return new CuentaResponseDTO(
            cuenta.getId(),
            cuenta.getNombreCuenta(),
            cuenta.getCorreoElectronico(),
            cuenta.getRolUsuario(),
            cuenta.getToken(),
            cuenta.getSaldo()
        );
    }
}
