package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaDominio.Excepciones.FirmaInvalidaException;
import com.CapaDominio.Excepciones.IdentidadDuplicadaException;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.NegocioInactivoException;
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
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManejadorExcepcionesHttpTest {

    @Test
    @DisplayName("traduce recurso inexistente a 404")
    void recursoNoEncontrado() {
        ManejadorExcepcionesHttp.Respuesta respuesta =
                ManejadorExcepcionesHttp.traducir(new RecursoNoEncontradoException("Producto no encontrado."));
        assertEquals(404, respuesta.status());
        assertEquals("Producto no encontrado.", respuesta.mensaje());
        assertFalse(respuesta.registrarSevero());
    }

    @Test
    @DisplayName("traduce conflictos de identidad, stock y reglas a 409")
    void conflictos() {
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new IdentidadDuplicadaException("duplicado")).status());
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new StockInsuficienteException("stock")).status());
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new ReglaNegocioException("regla")).status());
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new TransicionPagoInvalidaException("transicion")).status());
    }

    @Test
    @DisplayName("traduce negocio inactivo a 403 y pagos rechazados a 400")
    void negocioYPago() {
        assertEquals(403, ManejadorExcepcionesHttp.traducir(
                new NegocioInactivoException("inactivo")).status());
        assertEquals(400, ManejadorExcepcionesHttp.traducir(
                new PagoRechazadoException("rechazado")).status());
    }

    @Test
    @DisplayName("traduce la pasarela no disponible a 503 con advertencia")
    void pasarelaNoDisponible() {
        ManejadorExcepcionesHttp.Respuesta respuesta =
                ManejadorExcepcionesHttp.traducir(new PasarelaNoDisponibleException("sin red"));
        assertEquals(503, respuesta.status());
        assertTrue(respuesta.registrarAdvertencia());
    }

    @Test
    @DisplayName("traduce IO a 503 y errores desconocidos a 500")
    void ioYDesconocido() {
        assertEquals(503, ManejadorExcepcionesHttp.traducir(new IOException("corte")).status());
        ManejadorExcepcionesHttp.Respuesta desconocida =
                ManejadorExcepcionesHttp.traducir(new RuntimeException("boom"));
        assertEquals(500, desconocida.status());
        assertEquals(ManejadorExcepcionesHttp.MENSAJE_ERROR_INTERNO, desconocida.mensaje());
        assertFalse(desconocida.mensaje().contains("boom"));
        assertTrue(desconocida.registrarSevero());
    }

    @Test
    @DisplayName("traduce firma inválida, método de pago no soportado y validación")
    void firmaMetodoPagoYValidacion() {
        assertEquals(401, ManejadorExcepcionesHttp.traducir(
                new FirmaInvalidaException("firma inválida")).status());
        assertEquals(400, ManejadorExcepcionesHttp.traducir(
                new MetodoPagoNoSoportadoException("método no soportado")).status());
        assertEquals(400, ManejadorExcepcionesHttp.traducir(
                new ValidacionException("dato inválido")).status());
    }

    @Test
    @DisplayName("desenvuelve CompletionException antes de traducir")
    void desenvuelveFuturo() {
        Throwable error = new CompletionException(
                new CompletionException(new RecursoNoEncontradoException("venta")));
        assertEquals(404, ManejadorExcepcionesHttp.traducir(error).status());
    }

    @Test
    @DisplayName("traduce duplicidad SQL a 409 incluso si está envuelta")
    void duplicidadBd() {
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new SQLIntegrityConstraintViolationException("duplicado")).status());
        SQLException duplicado = new SQLException("duplicado", "23000", 1062);
        assertEquals(409, ManejadorExcepcionesHttp.traducir(
                new RuntimeException("Transacción abortada", duplicado)).status());
    }

    @Test
    @DisplayName("traduce validaciones de usuario a 400")
    void validacionA400() {
        assertEquals(400, ManejadorExcepcionesHttp.traducir(
                new IllegalArgumentException("dato inválido")).status());
    }

    @Test
    @DisplayName("respeta el status y el mensaje de ControllerException")
    void controllerException() {
        ManejadorExcepcionesHttp.Respuesta respuesta =
                ManejadorExcepcionesHttp.traducir(new ControllerException.Prohibido("no", "permiso"));
        assertEquals(403, respuesta.status());
        assertEquals("no", respuesta.mensaje());
    }

    @Test
    @DisplayName("construye el cuerpo de error con error, código y timestamp")
    void cuerpoErrorUnificado() {
        String cuerpo = ManejadorExcepcionesHttp.cuerpoError(404, "Producto no encontrado.");
        Map<?, ?> json = (Map<?, ?>) JsonUtils.parse(cuerpo);

        assertEquals("Producto no encontrado.", json.get("error"));
        assertEquals(404L, json.get("codigo"));
        assertNotNull(json.get("timestamp"));
        Instant.parse(json.get("timestamp").toString());
    }

    @Test
    @DisplayName("usa un mensaje genérico cuando no hay mensaje de error")
    void cuerpoErrorSinMensaje() {
        Map<?, ?> json = (Map<?, ?>) JsonUtils.parse(
                ManejadorExcepcionesHttp.cuerpoError(500, null));

        assertEquals("Error desconocido.", json.get("error"));
        assertEquals(500L, json.get("codigo"));
    }
}
