package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IPasarelaPago;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;

public class PasarelaEfectivo implements IPasarelaPago {

    @Override
    public void validarConfiguracion() {
    }

    @Override
    public void validar(Venta venta) throws PagoRechazadoException {
        if (venta == null || venta.calculoTotal().signum() <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
    }

    @Override
    public ResultadoPago procesar(Venta venta, String claveIdempotenciaPasarela)
            throws PagoRechazadoException, PasarelaNoDisponibleException {
        validar(venta);
        return ResultadoPago.pendiente(
                String.valueOf(venta.getId()),
                venta.calculoTotal(),
                "EFECTIVO",
                null,
                null,
                claveIdempotenciaPasarela,
                "El pago en efectivo requiere confirmación interna.");
    }

    @Override
    public TipoDePago getTipoSoportado() {
        return TipoDePago.EFECTIVO;
    }
}
