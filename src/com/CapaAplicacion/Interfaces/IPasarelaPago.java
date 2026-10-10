package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;

public interface IPasarelaPago {
    void validarConfiguracion();

    void validar(Venta venta) throws PagoRechazadoException;

    ResultadoPago procesar(Venta venta, String claveIdempotenciaPasarela)
            throws PagoRechazadoException, PasarelaNoDisponibleException;

    TipoDePago getTipoSoportado();
}
