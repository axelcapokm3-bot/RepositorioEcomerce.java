package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import com.CapaAplicacion.DTOentrada.CuentaIngresoDTO;
import com.CapaAplicacion.DTOentrada.CuentaRegistroDTO;
import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.AutenticacionService;
import com.CapaPresentacion.Utilidades.LimitadorTasa;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpExchange;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AuthController extends BaseController {
    private static final int LIMITE_POR_MINUTO = 20;
    private static final int LIMITE_LOGIN_POR_MINUTO = 10;
    private static final int LIMITE_REGISTRO_POR_CORREO = 5;
    private static final long VENTANA_REGISTRO_POR_CORREO_SEGUNDOS = 900L;

    public record VincularNegocioRequest(String dniCliente, String nombreCliente,
                                         String apellidoCliente, LocalDate fechaNacimientoCliente,
                                         String contraseniaPlana) {}

    private final AutenticacionService servicio;
    private final IProveedorTokenAcceso proveedor;

    public AuthController(AutenticacionService servicio, IProveedorTokenAcceso proveedor,
                          ResolutorNegocio resolutorNegocio) {
        super(resolutorNegocio);
        if (servicio == null || proveedor == null) {
            throw new IllegalArgumentException("El servicio de autenticación es obligatorio.");
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
            if (path.equals("/api/auth/sesion") || path.equals("/api/auth/sesion/")) {
                if (!"GET".equalsIgnoreCase(method)) {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta solo admite GET.");
                }
                Principal principal = exigirAutenticacion(exchange, proveedor);
                sendResponse(exchange, 200, Map.of(
                        "idCuenta", principal.idCuenta(),
                        "idCliente", principal.idCliente(),
                        "idNegocio", principal.idNegocio(),
                        "rol", principal.rol().name()));
                return;
            }
            if (path.equals("/api/auth/negocios") || path.equals("/api/auth/negocios/")) {
                if (!"GET".equalsIgnoreCase(method)) {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta solo admite GET.");
                }
                Principal principal = exigirAutenticacion(exchange, proveedor);
                handleAsync(exchange, servicio.listarNegociosAsync(principal), 200);
                return;
            }
            if (path.equals("/api/auth/salida") || path.equals("/api/auth/salida/")) {
                if (!"POST".equalsIgnoreCase(method)) {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta solo admite POST.");
                }
                Principal principalLimite = extraerPrincipal(exchange, proveedor).orElse(null);
                if (limitarSolicitud(exchange, principalLimite, "auth-salida", LIMITE_POR_MINUTO)) {
                    return;
                }
                exigirAutenticacion(exchange, proveedor);
                String token = extraerToken(exchange).orElseThrow(() -> new ControllerException.NoAutenticado(
                        "Se requiere autenticación.",
                        "Debe enviar un token válido en el encabezado Authorization."));
                proveedor.revocar(token);
                sendResponse(exchange, 200, Map.of("mensaje", "Sesión cerrada."));
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                throw new ControllerException.Validacion("Método no permitido.", "La ruta solo admite POST.");
            }
            Principal principalLimite = extraerPrincipal(exchange, proveedor).orElse(null);
            if (path.equals("/api/auth/ingreso") || path.equals("/api/auth/ingreso/")) {
                int maxIntentosLogin = LimitadorTasa.limiteConfigurado("auth-login", "max-intentos", 5);
                long bloqueoLogin = LimitadorTasa.bloqueoSegundos("auth-login", 60L);
                if (limitarIntentosPorIp(exchange, "auth-login", maxIntentosLogin, bloqueoLogin)) {
                    return;
                }
                if (limitarSolicitud(exchange, null, "auth-login", LIMITE_LOGIN_POR_MINUTO)) {
                    return;
                }
            } else if (path.equals("/api/auth/registro") || path.equals("/api/auth/registro/")) {
                if (limitarSolicitud(exchange, principalLimite, "auth-registro", LIMITE_POR_MINUTO)) {
                    return;
                }
            } else if (limitarSolicitud(exchange, principalLimite, "auth-cuenta", LIMITE_POR_MINUTO)) {
                return;
            }
            if (path.equals("/api/auth/registro") || path.equals("/api/auth/registro/")) {
                CuentaRegistroDTO dto = parseRequestBody(exchange, CuentaRegistroDTO.class);
                String correoNormalizado = dto.correoElectronico().trim().toLowerCase(java.util.Locale.ROOT);
                if (limitarSolicitudPorClave(exchange, "registro-email|" + correoNormalizado,
                        LIMITE_REGISTRO_POR_CORREO, VENTANA_REGISTRO_POR_CORREO_SEGUNDOS)) {
                    return;
                }
                long idNegocio = exigirNegocio(exchange);
                Optional<Principal> solicitante = extraerPrincipal(exchange, proveedor);
                String tokenSetup = exchange.getRequestHeaders().getFirst("X-Setup-Token");
                CompletableFuture<SesionResponseDTO> futuro = servicio.registrarAsync(idNegocio, dto,
                        solicitante.isPresent() ? solicitante.get() : null, tokenSetup);
                handleAsync(exchange, futuro, 201);
            } else if (path.equals("/api/auth/ingreso") || path.equals("/api/auth/ingreso/")) {
                CuentaIngresoDTO dto = parseRequestBody(exchange, CuentaIngresoDTO.class);
                long idNegocio = exigirNegocio(exchange);
                handleAsync(exchange, servicio.ingresarAsync(idNegocio, dto), 200);
            } else if (path.equals("/api/auth/negocio-activo") || path.equals("/api/auth/negocio-activo/")) {
                Principal principal = exigirAutenticacion(exchange, proveedor);
                long idNegocio = negocioObjetivo(exchange, principal);
                handleAsync(exchange, servicio.cambiarNegocioAsync(principal, idNegocio), 200);
            } else if (path.equals("/api/auth/vincular") || path.equals("/api/auth/vincular/")) {
                Principal principal = exigirAutenticacion(exchange, proveedor);
                VincularNegocioRequest req = parseRequestBody(exchange, VincularNegocioRequest.class);
                if (req == null
                        || req.dniCliente() == null || req.dniCliente().trim().isEmpty()
                        || req.nombreCliente() == null || req.nombreCliente().trim().isEmpty()
                        || req.apellidoCliente() == null || req.apellidoCliente().trim().isEmpty()
                        || req.fechaNacimientoCliente() == null
                        || req.contraseniaPlana() == null || req.contraseniaPlana().isEmpty()) {
                    throw new ControllerException.Validacion(
                            "Los datos de vinculación no son válidos.",
                            "Debe indicar el DNI y la identidad del titular, "
                            + "y confirmar su contraseña.");
                }
                long idNegocio = negocioObjetivo(exchange, principal);
                handleAsync(exchange, servicio.vincularNegocioAsync(principal, idNegocio,
                        req.dniCliente(), req.nombreCliente(), req.apellidoCliente(),
                        req.fechaNacimientoCliente(), req.contraseniaPlana()), 200);
            } else {
                throw new ControllerException.NoEncontrado("Ruta no encontrada.", "No existe un recurso para la URL solicitada.");
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

    private long negocioObjetivo(HttpExchange exchange, Principal principal) {
        String subdominio = exchange.getRequestHeaders().getFirst(ResolutorNegocio.HEADER_NEGOCIO);
        Optional<Long> resuelto = resolverNegocio(exchange);
        if (subdominio == null || subdominio.isBlank()) {
            return resuelto.orElse(principal.idNegocio());
        }
        return resuelto.orElseThrow(() -> new ControllerException.Validacion(
                "El negocio indicado no es válido.",
                "El subdominio no existe o no está activo."));
    }
}
