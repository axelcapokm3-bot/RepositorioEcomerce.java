package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPasarelaPago;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;

import java.util.Map;

public class PasarelaTransferencia implements IPasarelaPago {
    private final INegocioRepository negocioRepository;

    public PasarelaTransferencia(INegocioRepository negocioRepository) {
        this.negocioRepository = negocioRepository;
    }

    @Override
    public void validarConfiguracion() {
        if (negocioRepository == null) {
            throw new PagoRechazadoException(
                    "No está configurada la cuenta para recibir transferencias.");
        }

        String cbu = buscarConfiguracion("cbu_transferencia");
        if (cbu == null || cbu.isBlank()) {
            throw new PagoRechazadoException(
                    "La transferencia requiere un CBU configurado en el sistema.");
        }
    }

    @Override
    public void validar(Venta venta) throws PagoRechazadoException {
        if (venta == null || venta.calculoTotal().signum() <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
        validarConfiguracion();
    }

    @Override
    public ResultadoPago procesar(Venta venta, String claveIdempotenciaPasarela)
            throws PagoRechazadoException, PasarelaNoDisponibleException {
        validar(venta);
        String cbu = buscarConfiguracion("cbu_transferencia");
        return ResultadoPago.pendiente(
                String.valueOf(venta.getId()),
                venta.calculoTotal(),
                "TRANSFERENCIA",
                null,
                null,
                claveIdempotenciaPasarela,
                "Transferencia pendiente de validación interna. CBU: " + cbu);
    }

    private String buscarConfiguracion(String clave) {
        Map<ConfiguracionCatalogo, String> configuraciones =
                negocioRepository.listarConfiguracionesEfectivas(1);
        for (Map.Entry<ConfiguracionCatalogo, String> entrada : configuraciones.entrySet()) {
            if (clave.equalsIgnoreCase(entrada.getKey().getClave())) {
                return entrada.getValue();
            }
        }
        return null;
    }

    @Override
    public TipoDePago getTipoSoportado() {
        return TipoDePago.TRANSFERENCIA;
    }
}
