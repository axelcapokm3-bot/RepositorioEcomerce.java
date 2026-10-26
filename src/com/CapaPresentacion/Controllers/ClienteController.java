package com.CapaPresentacion.Controllers;

import java.io.IOException;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.IServiceCliente;
import com.CapaAplicacion.Seguridad.Principal;
import com.sun.net.httpserver.HttpExchange;

public class ClienteController extends BaseController {

    private static final int LIMITE_POR_MINUTO = 120;

    private final IServiceCliente servicio;
    private final IProveedorTokenAcceso proveedor;

    public ClienteController(IServiceCliente servicio, IProveedorTokenAcceso proveedor) {
        if (servicio == null || proveedor == null) {
            throw new IllegalArgumentException("El servicio y el proveedor de tokens son obligatorios.");
        }
        this.servicio = servicio;
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
            Principal principalLimite = extraerPrincipal(exchange, proveedor).orElse(null);
            if (limitarSolicitud(exchange, principalLimite, "clientes-admin", LIMITE_POR_MINUTO)) {
                return;
            }
            if (path.equals("/api/clientes") || path.equals("/api/clientes/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    handleAsync(exchange, servicio.listarTodosAsync(idNegocio), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(idNegocio, dto), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/clientes/");
                if (id == null) {
                    throw new ControllerException.Validacion("El ID de cliente no es válido.", "Debe ser un entero positivo.");
                }

                if ("GET".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirPermisoSobreCliente(principal, id);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    handleAsync(exchange, servicio.buscarPorIdAsync(idNegocio, id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirPermisoSobreCliente(principal, id);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
                    handleAsync(exchange, servicio.actualizarAsync(idNegocio, id, dto), 200);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    handleAsync(exchange, servicio.eliminarAsync(idNegocio, id), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            }
        } catch (ControllerException e) {
            manejarError(exchange, e);
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.", "Problema de red o comunicación."
            ).respuesta());
        } catch (Exception e) {
            manejarError(exchange, e);
        }
    }
}
