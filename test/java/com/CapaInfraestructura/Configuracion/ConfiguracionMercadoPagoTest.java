package com.CapaInfraestructura.Configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguracionMercadoPagoTest {
    private static final int ID_NEGOCIO = 7;
    private static final String CLAVE_TOKEN = "mercadopago.access-token";
    private static final String CLAVE_SECRETO = "mercadopago.webhook-secret";

    private final INegocioRepository negocioRepository = mock(INegocioRepository.class);

    @Test
    void priorizaPropiedadJavaSobreEntornoArchivoYBaseDeDatos() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(CLAVE_TOKEN, "valor-java"),
                Map.of(CLAVE_TOKEN, "valor-entorno"),
                Map.of(CLAVE_TOKEN, "valor-archivo"));
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(configuracionesDb("mercado_pago_access_token", "valor-db"));

        assertEquals("valor-java", configuracion.obtenerAccessToken());
        verify(negocioRepository, never()).listarConfiguracionesEfectivas(ID_NEGOCIO);
    }

    @Test
    void priorizaEntornoSobreArchivoYBaseDeDatos() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(),
                Map.of(CLAVE_TOKEN, "valor-entorno"),
                Map.of(CLAVE_TOKEN, "valor-archivo"));
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(configuracionesDb("mercado_pago_access_token", "valor-db"));

        assertEquals("valor-entorno", configuracion.obtenerAccessToken());
        verify(negocioRepository, never()).listarConfiguracionesEfectivas(ID_NEGOCIO);
    }

    @Test
    void priorizaArchivoSobreBaseDeDatos() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(),
                Map.of(),
                Map.of(CLAVE_TOKEN, "valor-archivo"));
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(configuracionesDb("mercado_pago_access_token", "valor-db"));

        assertEquals("valor-archivo", configuracion.obtenerAccessToken());
        verify(negocioRepository, never()).listarConfiguracionesEfectivas(ID_NEGOCIO);
    }

    @Test
    void ignoraFuentesNulasOVaciasYContinuaHastaDb() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(CLAVE_SECRETO, " "),
                Map.of(),
                Map.of(CLAVE_SECRETO, ""));
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(configuracionesDb("mercado_pago_webhook_secret", "valor-db-antiguo"));

        assertEquals("valor-db-antiguo", configuracion.obtenerSecretoWebhook());
    }

    @Test
    void prefiereClaveCanonicaDbYConservaCompatibilidadConClaveAntigua() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(),
                Map.of(),
                Map.of());
        Map.Entry<ConfiguracionCatalogo, String> canonica =
                entradaDb("mercadopago.access-token", "valor-canonico-db");
        Map.Entry<ConfiguracionCatalogo, String> antigua =
                entradaDb("mercado_pago_access_token", "valor-antiguo-db");
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(Map.of(
                        canonica.getKey(), canonica.getValue(),
                        antigua.getKey(), antigua.getValue()));

        assertEquals("valor-canonico-db", configuracion.obtenerAccessToken());
    }

    @Test
    void devuelveNuloSiNoHayValorEnNingunaFuente() {
        ConfiguracionMercadoPago configuracion = crearConfiguracion(
                Map.of(),
                Map.of(),
                Map.of());
        when(negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO))
                .thenReturn(configuracionesDb("mercado_pago_webhook_secret", " \t"));

        assertNull(configuracion.obtenerSecretoWebhook());
    }

    private ConfiguracionMercadoPago crearConfiguracion(
            Map<String, String> propiedadesJava,
            Map<String, String> variablesEntorno,
            Map<String, String> archivo) {
        return new ConfiguracionMercadoPago(
                negocioRepository,
                ID_NEGOCIO,
                propiedadesJava::get,
                variablesEntorno::get,
                archivo::get);
    }

    private static Map<ConfiguracionCatalogo, String> configuracionesDb(
            String clave,
            String valor) {
        Map.Entry<ConfiguracionCatalogo, String> entrada = entradaDb(clave, valor);
        return Map.of(entrada.getKey(), entrada.getValue());
    }

    private static Map.Entry<ConfiguracionCatalogo, String> entradaDb(
            String clave,
            String valor) {
        return Map.entry(
                new ConfiguracionCatalogo(1, clave, "string", "", "", "pagos"),
                valor);
    }
}
