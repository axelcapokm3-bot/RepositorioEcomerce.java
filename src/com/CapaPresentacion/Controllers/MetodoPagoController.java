package com.CapaPresentacion.Controllers;

import java.io.IOException;

import com.CapaAplicacion.DTOentrada.MetodoPagoRequestDTO;
import com.CapaAplicacion.DTOsalida.MetodoPagoResponseDTO;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.MetodoPagoService;
import com.sun.net.httpserver.HttpExchange;

public class MetodoPagoController extends BaseController {

    private final MetodoPagoService metodoPagoService;
    private final IProveedorTokenAcceso proveedor;

    public MetodoPagoController(MetodoPagoService metodoPagoService, IProveedorTokenAcceso proveedor) {
        if (metodoPagoService == null || proveedor == null) {
            throw new IllegalArgumentException("El servicio y el proveedor de tokens son obligatorios.");
        }
        this.metodoPagoService = metodoPagoService;
        this.proveedor = proveedor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("POST".equalsIgnoreCase(method)) {
                handleCrear(exchange);
            } else if ("GET".equalsIgnoreCase(method)) {
                handleObtener(exchange, path);
            } else {
                throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
            }
        } catch (ControllerException e) {
            sendError(exchange, e.status(), e.respuesta());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, new ControllerException.Validacion(
                    "Los datos del método de pago no son válidos.",
                    "El formato o valor recibido no cumple las reglas del negocio."
            ).respuesta());
        } catch (IllegalStateException e) {
            sendError(exchange, 409, new ControllerException.Conflicto(
                    "El método de pago no puede asociarse en el estado actual.",
                    "La regla de negocio impide completar la operación."
            ).respuesta());
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.", "Problema de red o comunicación."
            ).respuesta());
        } catch (Exception e) {
            sendError(exchange, 500, "Código: ERROR_SERVIDOR | Mensaje: Error interno del servidor. | Causa: Fallo inesperado.");
        }
    }

    private void handleCrear(HttpExchange exchange) throws IOException {
        Principal principal = exigirAutenticacion(exchange, proveedor);
        MetodoPagoRequestDTO request = parseRequestBody(exchange, MetodoPagoRequestDTO.class);
        if (request == null || request.idCliente() <= 0 || request.claveTarjeta() <= 0) {
            throw new ControllerException.Validacion(
                "Los datos del método de pago son obligatorios.",
                "Debe indicar un cliente y una clave de tarjeta positivos."
            );
        }
        exigirPermisoSobreCliente(principal, request.idCliente());
        
        var future = metodoPagoService.asociarMetodoPagoAsync(request.idCliente(), request.claveTarjeta())
                .thenApply(v -> "{\"mensaje\": \"Método de pago asociado con éxito\"}");
        
        handleAsync(exchange, future, 201);
    }

    private void handleObtener(HttpExchange exchange, String path) throws IOException {
        String[] partes = path.split("/");
        if (partes.length < 4) {
            throw new ControllerException.Validacion("El ID de cliente es obligatorio.", "No fue proporcionado en la ruta.");
        }

        int idCliente;
        try {
            idCliente = Integer.parseInt(partes[partes.length - 1]);
        } catch (NumberFormatException e) {
            throw new ControllerException.Validacion("El ID de cliente no es válido.", "Debe ser un número entero positivo.");
        }
        if (idCliente <= 0) {
            throw new ControllerException.Validacion("El ID de cliente no es válido.", "Debe ser un número entero positivo.");
        }
        exigirPermisoSobreCliente(exigirAutenticacion(exchange, proveedor), idCliente);

        var future = metodoPagoService.obtenerPorClienteAsync(idCliente)
                .thenApply(optMetodo -> optMetodo.map(mp -> new MetodoPagoResponseDTO(mp.getIdCliente(), mp.getSaldoDisponible())));

        handleAsync(exchange, future, 200);
    }
}
