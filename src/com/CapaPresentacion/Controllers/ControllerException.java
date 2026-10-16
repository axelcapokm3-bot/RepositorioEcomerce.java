package com.CapaPresentacion.Controllers;

public abstract class ControllerException extends RuntimeException {

    private final int status;
    private final String codigo;
    private final String causa;

    protected ControllerException(int status, String codigo, String mensaje, String causa) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.causa = causa;
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public String causa() {
        return causa;
    }

    public String respuesta() {
        return "Código: " + codigo
                + " | Mensaje: " + getMessage()
                + " | Causa: " + causa;
    }

    public static final class Validacion extends ControllerException {
        public Validacion(String mensaje, String causa) {
            super(400, "ERROR_VALIDACION", mensaje, causa);
        }
    }

    public static final class NoEncontrado extends ControllerException {
        public NoEncontrado(String mensaje, String causa) {
            super(404, "RECURSO_NO_ENCONTRADO", mensaje, causa);
        }
    }

    public static final class NoAutenticado extends ControllerException {
        public NoAutenticado(String mensaje, String causa) {
            super(401, "NO_AUTENTICADO", mensaje, causa);
        }
    }

    public static final class Prohibido extends ControllerException {
        public Prohibido(String mensaje, String causa) {
            super(403, "PROHIBIDO", mensaje, causa);
        }
    }

    public static final class Conflicto extends ControllerException {
        public Conflicto(String mensaje, String causa) {
            super(409, "CONFLICTO_NEGOCIO", mensaje, causa);
        }
    }

    public static final class ServicioNoDisponible extends ControllerException {
        public ServicioNoDisponible(String mensaje, String causa) {
            super(503, "SERVICIO_NO_DISPONIBLE", mensaje, causa);
        }
    }
}
