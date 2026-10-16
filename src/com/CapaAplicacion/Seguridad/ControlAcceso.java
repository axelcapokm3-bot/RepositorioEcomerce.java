package com.CapaAplicacion.Seguridad;

public final class ControlAcceso {
    private ControlAcceso() {
    }

    public static boolean esAdmin(Principal principal) {
        return principal != null && principal.esAdmin();
    }

    public static boolean puedeOperarCliente(Principal principal, int idCliente) {
        if (principal == null || idCliente <= 0) {
            return false;
        }
        return principal.esAdmin() || principal.idCliente() == idCliente;
    }

    public static boolean puedeVerVenta(Principal principal, int idClienteDuenio) {
        return puedeOperarCliente(principal, idClienteDuenio);
    }

    public static boolean puedeOperarNegocio(Principal principal, long idNegocio) {
        if (principal == null || idNegocio <= 0) {
            return false;
        }
        return principal.idNegocio() == idNegocio;
    }
}
