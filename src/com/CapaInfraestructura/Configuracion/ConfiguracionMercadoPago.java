package com.CapaInfraestructura.Configuracion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;

import java.util.Map;

/** Resuelve las credenciales de Mercado Pago con precedencia uniforme. */
public final class ConfiguracionMercadoPago {
    private static final String CLAVE_ACCESS_TOKEN = "mercadopago.access-token";
    private static final String CLAVE_SECRETO_WEBHOOK = "mercadopago.webhook-secret";
    private static final String CLAVE_DB_ACCESS_TOKEN_ANTIGUA = "mercado_pago_access_token";
    private static final String CLAVE_DB_SECRETO_WEBHOOK_ANTIGUA = "mercado_pago_webhook_secret";

    private final INegocioRepository negocioRepository;
    private final long idNegocio;
    private final ResolutorConfiguracion resolutor;

    public ConfiguracionMercadoPago(INegocioRepository negocioRepository, long idNegocio) {
        this(negocioRepository, idNegocio, ResolutorConfiguracion.getInstancia());
    }

    ConfiguracionMercadoPago(
            INegocioRepository negocioRepository,
            long idNegocio,
            ResolutorConfiguracion resolutor) {
        this.negocioRepository = negocioRepository;
        this.idNegocio = idNegocio;
        this.resolutor = resolutor;
    }

    public String obtenerAccessToken() {
        return resolver(CLAVE_ACCESS_TOKEN, CLAVE_DB_ACCESS_TOKEN_ANTIGUA);
    }

    public String obtenerSecretoWebhook() {
        return resolver(CLAVE_SECRETO_WEBHOOK, CLAVE_DB_SECRETO_WEBHOOK_ANTIGUA);
    }

    private String resolver(String clave, String claveAntiguaDb) {
        String valor = resolutor.obtener(clave);
        if (valor != null) {
            return valor;
        }
        return obtenerValorDb(clave, claveAntiguaDb);
    }

    private String obtenerValorDb(String clave, String claveAntigua) {
        if (negocioRepository == null) {
            return null;
        }

        Map<ConfiguracionCatalogo, String> configuraciones =
                negocioRepository.listarConfiguracionesEfectivas(idNegocio);
        String valor = buscarValor(configuraciones, clave);
        return valor != null ? valor : buscarValor(configuraciones, claveAntigua);
    }

    private static String buscarValor(
            Map<ConfiguracionCatalogo, String> configuraciones,
            String claveBuscada) {
        for (Map.Entry<ConfiguracionCatalogo, String> entrada : configuraciones.entrySet()) {
            ConfiguracionCatalogo configuracion = entrada.getKey();
            if (configuracion != null
                    && claveBuscada.equalsIgnoreCase(configuracion.getClave())) {
                String valor = entrada.getValue();
                return valor == null || valor.isBlank() ? null : valor;
            }
        }
        return null;
    }
}
