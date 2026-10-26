package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.DTOentrada.ConfiguracionRequestDTO;
import com.CapaAplicacion.DTOsalida.ConfiguracionResponseDTO;
import com.CapaAplicacion.Interfaces.INegocioService;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.Negocio;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NegocioConfigController extends BaseController {
    private final INegocioService negocioService;
    private final IProveedorTokenAcceso proveedor;
    private static final Pattern PATTERN_CONFIG = Pattern.compile("^/api/negocios/([^/]+)/configuraciones/?$");

    public NegocioConfigController(INegocioService negocioService, IProveedorTokenAcceso proveedor) {
        if (negocioService == null || proveedor == null) {
            throw new IllegalArgumentException("El servicio y el proveedor de tokens son obligatorios.");
        }
        this.negocioService = negocioService;
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
            Principal principal = exigirAutenticacion(exchange, proveedor);
            exigirAdmin(principal);
            Matcher matcher = PATTERN_CONFIG.matcher(path);
            if (matcher.matches()) {
                String subdominio = matcher.group(1);
                Negocio negocio = negocioService.buscarNegocioPorSubdominio(subdominio)
                        .orElseThrow(() -> new com.CapaDominio.Excepciones.RecursoNoEncontradoException(
                                "No existe un negocio con el subdominio solicitado."));
                exigirPermisoSobreNegocio(principal, negocio.getId());

                if ("GET".equalsIgnoreCase(method)) {
                    sendResponse(exchange, 200, redactarSecretos(negocioService.listarConfiguraciones(subdominio)));
                } else if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    ConfiguracionRequestDTO dto = parseRequestBody(exchange, ConfiguracionRequestDTO.class);
                    if (dto == null || dto.getClave() == null) {
                        throw new ControllerException.Validacion("Datos incompletos.", "La clave y el valor son obligatorios.");
                    }
                    negocioService.actualizarConfiguracion(subdominio, dto.getClave(), dto.getValor());
                    sendResponse(exchange, 200, "{\"mensaje\": \"Configuración actualizada correctamente\"}");
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                throw new ControllerException.NoEncontrado("Ruta no encontrada.", "La ruta solicitada no existe.");
            }
        } catch (ControllerException e) {
            manejarError(exchange, e);
        } catch (IllegalArgumentException | com.CapaDominio.Excepciones.ReglaNegocioException e) {
            sendError(exchange, 400, new ControllerException.Validacion(
                    "Los datos de configuración no son válidos.",
                    "La regla de negocio rechazó la operación.").respuesta());
        } catch (com.CapaDominio.Excepciones.RecursoNoEncontradoException e) {
            sendError(exchange, 404, new ControllerException.NoEncontrado(
                    "Recurso no encontrado.",
                    "No existe el negocio o la configuración solicitada.").respuesta());
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.", "Problema de red o comunicación."
            ).respuesta());
        } catch (Exception e) {
            manejarError(exchange, e);
        }
    }

    private List<ConfiguracionResponseDTO> redactarSecretos(List<ConfiguracionResponseDTO> configuraciones) {
        List<ConfiguracionResponseDTO> redactadas = new ArrayList<>();
        if (configuraciones == null) {
            return redactadas;
        }
        for (int i = 0; i < configuraciones.size(); i++) {
            ConfiguracionResponseDTO dto = configuraciones.get(i);
            String valor = dto.getValor();
            if (dto.getClave() != null && esClaveSensible(dto.getClave())) {
                valor = "***";
            }
            redactadas.add(new ConfiguracionResponseDTO(dto.getClave(), dto.getTipo(),
                    dto.getDescripcion(), valor, dto.getCategoria()));
        }
        return redactadas;
    }

    private boolean esClaveSensible(String clave) {
        String normalizada = clave.toLowerCase(java.util.Locale.ROOT);
        return normalizada.contains("secret") || normalizada.contains("token")
                || normalizada.contains("password") || normalizada.contains("access");
    }
}
