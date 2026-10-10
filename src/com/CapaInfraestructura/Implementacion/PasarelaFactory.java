package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPasarelaPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Excepciones.ReglaNegocioException;

import java.util.EnumMap;
import java.util.Map;

public class PasarelaFactory {
    private static final Map<TipoDePago, IPasarelaPago> cache =
            new EnumMap<>(TipoDePago.class);
    private static INegocioRepository negocioRepositoryStatic;

    public static synchronized void inicializar(INegocioRepository negocioRepo) {
        negocioRepositoryStatic = negocioRepo;
        cache.clear();
        cache.put(TipoDePago.EFECTIVO, new PasarelaEfectivo());
        cache.put(TipoDePago.TRANSFERENCIA, new PasarelaTransferencia(negocioRepo));
        cache.put(TipoDePago.MERCADO_PAGO, new PasarelaMercadoPago(1, negocioRepo));
        cache.put(TipoDePago.STRIPE, new PasarelaStripe(negocioRepo));
    }

    public static synchronized IPasarelaPago obtener(TipoDePago tipo) {
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de pago no puede ser nulo.");
        }
        IPasarelaPago pasarela = cache.get(tipo);
        if (pasarela != null) {
            return pasarela;
        }

        switch (tipo) {
            case EFECTIVO:
                pasarela = new PasarelaEfectivo();
                break;
            case TRANSFERENCIA:
                pasarela = new PasarelaTransferencia(negocioRepositoryStatic);
                break;
            case MERCADO_PAGO:
                pasarela = new PasarelaMercadoPago(1, negocioRepositoryStatic);
                break;
            case STRIPE:
                pasarela = new PasarelaStripe(negocioRepositoryStatic);
                break;
            default:
                throw new ReglaNegocioException("Tipo de pago no soportado: " + tipo);
        }

        cache.put(tipo, pasarela);
        return pasarela;
    }
}
