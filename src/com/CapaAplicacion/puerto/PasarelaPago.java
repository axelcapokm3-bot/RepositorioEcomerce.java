package com.CapaAplicacion.puerto;

import java.util.Map;

public interface PasarelaPago {
    ResultadoInicio iniciarCobro(SolicitudCobro solicitud);

    EstadoPagoExterno consultarEstado(String idExterno);

    EventoWebhook parsearWebhook(String payload, Map<String, String> headers, String secreto);
}
