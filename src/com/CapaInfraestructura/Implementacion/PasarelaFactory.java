package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPasarelaPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Excepciones.ReglaNegocioException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PasarelaFactory {
    private static final Map<String, IPasarelaPago> cache = new ConcurrentHashMap<>();
    private static INegocioRepository negocioRepositoryStatic;

    public static synchronized void inicializar(INegocioRepository negocioRepo) {
        negocioRepositoryStatic = negocioRepo;
        cache.clear();
        cache.put(clave(0L, TipoDePago.EFECTIVO), new PasarelaEfectivo());
    }

    public static synchronized IPasarelaPago obtener(long idNegocio, TipoDePago tipo) {
        if (idNegocio <= 0) {
            throw new ReglaNegocioException("El negocio del pago no es válido.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de pago no puede ser nulo.");
        }
        IPasarelaPago pasarela = cache.get(clave(idNegocio, tipo));
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
                pasarela = new PasarelaMercadoPago(idNegocio, negocioRepositoryStatic);
                break;
            case STRIPE:
                pasarela = new PasarelaStripe(idNegocio, negocioRepositoryStatic);
                break;
            case TARJETA_CREDITO:
                throw new ReglaNegocioException(
                        "El pago con tarjeta de crédito no está disponible. Use Mercado Pago o Stripe.");
            default:
                throw new ReglaNegocioException("Tipo de pago no soportado: " + tipo);
        }

        cache.put(clave(idNegocio, tipo), pasarela);
        return pasarela;
    }

    private static String clave(long idNegocio, TipoDePago tipo) {
        return idNegocio + ":" + tipo.name();
    }
}
