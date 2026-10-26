package com.CapaPresentacion.Controllers;

import com.CapaDominio.Excepciones.CarritoVacioException;
import com.CapaDominio.Excepciones.FirmaInvalidaException;
import com.CapaDominio.Excepciones.IdentidadDuplicadaException;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.NegocioInactivoException;
import com.CapaDominio.Excepciones.PagoInvalidoException;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.ReglaNegocioException;
import com.CapaDominio.Excepciones.StockInsuficienteException;
import com.CapaDominio.Excepciones.TransicionPagoInvalidaException;
import com.CapaDominio.Excepciones.ValidacionException;
import com.CapaPresentacion.Utilidades.JsonUtils;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Traduce excepciones de la aplicación a respuestas HTTP explícitas. */
public final class ManejadorExcepcionesHttp {

    public static final String MENSAJE_ERROR_INTERNO = "Error interno del servidor.";
    private static final String MENSAJE_ERROR_DESCONOCIDO = "Error desconocido.";

    private ManejadorExcepcionesHttp() {
    }

    public record Respuesta(int status, String mensaje, boolean registrarSevero,
                            boolean registrarAdvertencia, Throwable causaParaLog) {
    }

    public static String cuerpoError(int status, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", mensaje == null || mensaje.isBlank()
                ? MENSAJE_ERROR_DESCONOCIDO
                : mensaje);
        cuerpo.put("codigo", status);
        cuerpo.put("timestamp", Instant.now().toString());
        return JsonUtils.toJson(cuerpo);
    }

    public static Respuesta traducir(Throwable error) {
        Throwable causa = desenvolver(error);
        if (causa instanceof ControllerException controllerException) {
            return new Respuesta(controllerException.status(),
                    mensajeDe(controllerException, "Error de solicitud."),
                    false, false, causa);
        }
        if (causa instanceof ValidacionException) {
            return new Respuesta(400, mensajeDe(causa, "Los datos enviados no son válidos."),
                    false, false, causa);
        }
        if (causa instanceof FirmaInvalidaException) {
            return new Respuesta(401, mensajeDe(causa, "La firma de la solicitud no es válida."),
                    false, false, causa);
        }
        if (causa instanceof MetodoPagoNoSoportadoException) {
            return new Respuesta(400, mensajeDe(causa, "El método de pago no está soportado."),
                    false, false, causa);
        }
        if (esDuplicidadBd(causa)) {
            return new Respuesta(409,
                    "La operación entra en conflicto con un registro existente.",
                    false, false, causa);
        }
        if (causa instanceof PasarelaNoDisponibleException) {
            return new Respuesta(503, "La pasarela seleccionada no está disponible.",
                    false, true, causa);
        }
        if (causa instanceof PagoRechazadoException) {
            return new Respuesta(400, mensajeDe(causa, "La pasarela rechazó los datos del checkout."),
                    false, false, causa);
        }
        if (causa instanceof PagoInvalidoException) {
            return new Respuesta(400, mensajeDe(causa, "Los datos del pago no son válidos."),
                    false, false, causa);
        }
        if (causa instanceof StockInsuficienteException) {
            return new Respuesta(409, mensajeDe(causa,
                    "El stock disponible ya no alcanza para completar la operación."),
                    false, false, causa);
        }
        if (causa instanceof NegocioInactivoException) {
            return new Respuesta(403, "El negocio no está disponible.",
                    false, false, causa);
        }
        if (causa instanceof RecursoNoEncontradoException) {
            return new Respuesta(404, mensajeDe(causa, "El recurso solicitado no existe."),
                    false, false, causa);
        }
        if (causa instanceof IdentidadDuplicadaException) {
            return new Respuesta(409, mensajeDe(causa, "La identidad ya está registrada en este negocio."),
                    false, false, causa);
        }
        if (causa instanceof TransicionPagoInvalidaException) {
            return new Respuesta(409, mensajeDe(causa, "El pago no puede cambiar al estado solicitado."),
                    false, false, causa);
        }
        if (causa instanceof CarritoVacioException) {
            return new Respuesta(409, mensajeDe(causa, "El carrito debe contener al menos un producto."),
                    false, false, causa);
        }
        if (causa instanceof ReglaNegocioException) {
            return new Respuesta(409, mensajeDe(causa, "La regla de negocio impide completar la operación."),
                    false, false, causa);
        }
        if (causa instanceof IllegalArgumentException) {
            return new Respuesta(400, "Los datos enviados no son válidos.",
                    false, false, causa);
        }
        if (causa instanceof IllegalStateException) {
            return new Respuesta(409, "La operación no puede realizarse en el estado actual.",
                    false, false, causa);
        }
        if (causa instanceof IOException) {
            return new Respuesta(503, "No fue posible comunicarse con el servicio requerido.",
                    false, false, causa);
        }
        return new Respuesta(500, MENSAJE_ERROR_INTERNO, true, false, causa);
    }

    public static Throwable desenvolver(Throwable error) {
        Throwable causa = error;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException)
                && causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa;
    }

    private static String mensajeDe(Throwable causa, String respaldo) {
        String mensaje = causa.getMessage();
        return mensaje == null || mensaje.isBlank() ? respaldo : mensaje;
    }

    private static boolean esDuplicidadBd(Throwable error) {
        Throwable causa = error;
        for (int nivel = 0; causa != null && nivel < 12; nivel++) {
            if (causa instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (causa instanceof SQLException sqlException) {
                if ("23000".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 1062) {
                    return true;
                }
            }
            causa = causa.getCause();
        }
        return false;
    }
}
