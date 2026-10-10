package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.DTOentrada.ConfiguracionRequestDTO;
import com.CapaAplicacion.Interfaces.INegocioService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NegocioConfigController extends BaseController {
    private final INegocioService negocioService;
    private static final Pattern PATTERN_CONFIG = Pattern.compile("^/api/negocios/([^/]+)/configuraciones/?$");

    public NegocioConfigController(INegocioService negocioService) {
        this.negocioService = negocioService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            Matcher matcher = PATTERN_CONFIG.matcher(path);
            if (matcher.matches()) {
                String subdominio = matcher.group(1);

                if ("GET".equalsIgnoreCase(method)) {
                    sendResponse(exchange, 200, negocioService.listarConfiguraciones(subdominio));
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
        } catch (IllegalArgumentException | com.CapaDominio.Excepciones.ReglaNegocioException e) {
            throw new ControllerException.Validacion(e.getMessage(), "Error de validación de negocio.");
        } catch (com.CapaDominio.Excepciones.RecursoNoEncontradoException e) {
            throw new ControllerException.NoEncontrado(e.getMessage(), "Recurso no encontrado.");
        }
    }
}
