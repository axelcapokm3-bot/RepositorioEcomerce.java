package com.CapaAplicacion.puerto;

public interface ConfiguracionCobro {
    String urlRetorno(long idNegocio, ProveedorPago proveedor);

    String urlCancelacion(long idNegocio, ProveedorPago proveedor);

    String urlPendiente(long idNegocio, ProveedorPago proveedor);

    String urlWebhook(long idNegocio, ProveedorPago proveedor);
}
