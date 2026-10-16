package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.CuentaIngresoDTO;
import com.CapaAplicacion.DTOentrada.CuentaRegistroDTO;
import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.DTOsalida.VinculoResponseDTO;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaNegocioRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IServicioContrasena;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaDominio.Entidades.VinculoCuentaNegocio;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class AutenticacionService {
    private static final String HASH_FALSO_PARA_MEDICION_CONSTANTE =
            "pbkdf2$210000$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final ICuentaRepositorio cuentaRepositorio;
    private final IClienteRepositorio clienteRepositorio;
    private final ICuentaNegocioRepositorio vinculoRepositorio;
    private final INegocioRepository negocioRepositorio;
    private final IServicioContrasena servicioContrasena;
    private final IProveedorTokenAcceso proveedorToken;
    private final IServiceExecutor executor;
    private final String tokenBootstrap;

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken) {
        this(cuentaRepositorio, clienteRepositorio, vinculoRepositorio, negocioRepositorio,
                servicioContrasena, proveedorToken,
                RegistroExecutors.obtener(TipoServicio.AUTENTICACION));
    }

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken,
                                IServiceExecutor executor) {
        this(cuentaRepositorio, clienteRepositorio, vinculoRepositorio, negocioRepositorio,
                servicioContrasena, proveedorToken, executor, null);
    }

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken,
                                IServiceExecutor executor,
                                String tokenBootstrap) {
        if (cuentaRepositorio == null || clienteRepositorio == null || vinculoRepositorio == null
                || negocioRepositorio == null
                || servicioContrasena == null || proveedorToken == null || executor == null) {
            throw new IllegalArgumentException("Las dependencias de autenticación son obligatorias.");
        }
        this.cuentaRepositorio = cuentaRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.vinculoRepositorio = vinculoRepositorio;
        this.negocioRepositorio = negocioRepositorio;
        this.servicioContrasena = servicioContrasena;
        this.proveedorToken = proveedorToken;
        this.executor = executor;
        this.tokenBootstrap = tokenBootstrap == null || tokenBootstrap.isBlank() ? null : tokenBootstrap;
    }

    public CompletableFuture<SesionResponseDTO> registrarAsync(final long idNegocio,
                                                                final CuentaRegistroDTO dto,
                                                                final Principal solicitante) {
        return registrarAsync(idNegocio, dto, solicitante, null);
    }

    public CompletableFuture<SesionResponseDTO> registrarAsync(final long idNegocio,
                                                                final CuentaRegistroDTO dto,
                                                                final Principal solicitante,
                                                                final String tokenSetup) {
        return executor.ejecutar(new Callable<SesionResponseDTO>() {
            @Override
            public SesionResponseDTO call() {
                return registrar(idNegocio, dto, solicitante, tokenSetup);
            }
        });
    }

    public CompletableFuture<SesionResponseDTO> ingresarAsync(final long idNegocio, final CuentaIngresoDTO dto) {
        return executor.ejecutar(new Callable<SesionResponseDTO>() {
            @Override
            public SesionResponseDTO call() {
                return ingresar(idNegocio, dto);
            }
        });
    }

    public CompletableFuture<SesionResponseDTO> vincularNegocioAsync(final Principal solicitante,
                                                                     final long idNegocio,
                                                                     final int idCliente,
                                                                     final String contraseniaPlana) {
        return executor.ejecutar(new Callable<SesionResponseDTO>() {
            @Override
            public SesionResponseDTO call() {
                return vincularNegocio(solicitante, idNegocio, idCliente, contraseniaPlana);
            }
        });
    }

    public CompletableFuture<SesionResponseDTO> cambiarNegocioAsync(final Principal solicitante,
                                                                    final long idNegocio) {
        return executor.ejecutar(new Callable<SesionResponseDTO>() {
            @Override
            public SesionResponseDTO call() {
                return cambiarNegocio(solicitante, idNegocio);
            }
        });
    }

    public CompletableFuture<List<VinculoResponseDTO>> listarNegociosAsync(final Principal solicitante) {
        return executor.ejecutar(new Callable<List<VinculoResponseDTO>>() {
            @Override
            public List<VinculoResponseDTO> call() {
                return listarNegocios(solicitante);
            }
        });
    }

    SesionResponseDTO registrar(long idNegocio, CuentaRegistroDTO dto, Principal solicitante, String tokenSetup) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos de registro son obligatorios.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio del registro no es válido.");
        }
        if (solicitante != null && solicitante.idNegocio() != idNegocio) {
            throw new IllegalStateException("El solicitante no pertenece al negocio indicado.");
        }
        RolUsuario rol = resolverRol(dto.rol(), solicitante, tokenSetup);
        String correo = dto.correoElectronico().trim().toLowerCase();
        Optional<Cuenta> existente = cuentaRepositorio.buscarPorCorreo(correo);
        Cuenta cuenta;
        if (existente.isPresent()) {
            cuenta = existente.get();
            exigirCuentaActiva(cuenta);
            boolean esAltaAdmin = rol == RolUsuario.ADMIN && solicitante != null
                    && solicitante.esAdmin() && solicitante.idNegocio() == idNegocio;
            if (!esAltaAdmin) {
                throw new IllegalStateException(
                        "El correo electrónico ya está registrado. Ingrese con su cuenta para vincular este negocio.");
            }
        } else {
            String hash = servicioContrasena.generarHash(dto.contraseniaPlana());
            cuenta = new Cuenta(0, dto.nombreCuenta(), correo, hash);
            cuentaRepositorio.guardar(cuenta);
            if (cuenta.getId() <= 0) {
                Optional<Cuenta> creada = cuentaRepositorio.buscarPorCorreo(correo);
                if (creada.isEmpty()) {
                    throw new IllegalStateException("No se pudo crear la cuenta.");
                }
                cuenta = creada.get();
            }
        }
        validarPerfil(idNegocio, dto.idCliente());
        if (vinculoRepositorio.existeVinculo(cuenta.getId(), idNegocio)) {
            throw new IllegalStateException("La cuenta ya está vinculada a este negocio.");
        }
        VinculoCuentaNegocio vinculo =
                new VinculoCuentaNegocio(0, cuenta.getId(), idNegocio, dto.idCliente(), rol);
        vinculoRepositorio.guardar(vinculo);
        return emitirSesion(vinculo);
    }

    SesionResponseDTO ingresar(long idNegocio, CuentaIngresoDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos de ingreso son obligatorios.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio del ingreso no es válido.");
        }
        String correo = dto.correoElectronico().trim().toLowerCase();
        Optional<Cuenta> encontrada = cuentaRepositorio.buscarPorCorreo(correo);
        if (encontrada.isEmpty()) {
            servicioContrasena.verificar(dto.contraseniaPlana(), HASH_FALSO_PARA_MEDICION_CONSTANTE);
            throw new IllegalArgumentException("Las credenciales no son válidas.");
        }
        Cuenta cuenta = encontrada.get();
        if (!servicioContrasena.verificar(dto.contraseniaPlana(), cuenta.getHashContrasenia())) {
            throw new IllegalArgumentException("Las credenciales no son válidas.");
        }
        exigirCuentaActiva(cuenta);
        Optional<VinculoCuentaNegocio> acceso =
                vinculoRepositorio.buscarPorCuentaYNegocio(cuenta.getId(), idNegocio);
        if (acceso.isEmpty()) {
            throw new IllegalStateException("La cuenta no tiene acceso a este negocio.");
        }
        VinculoCuentaNegocio vinculo = acceso.get();
        exigirVinculoActivo(vinculo);
        return emitirSesion(vinculo);
    }

    SesionResponseDTO vincularNegocio(Principal solicitante, long idNegocio, int idCliente,
                                      String contraseniaPlana) {
        if (solicitante == null) {
            throw new IllegalArgumentException("Se requiere autenticación para vincular un negocio.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio a vincular no es válido.");
        }
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El perfil de cliente a vincular no es válido.");
        }
        if (contraseniaPlana == null || contraseniaPlana.isEmpty()) {
            throw new IllegalArgumentException("Debe confirmar su contraseña para vincular un negocio.");
        }
        Optional<Cuenta> encontrada = cuentaRepositorio.buscarPorId(solicitante.idCuenta());
        if (encontrada.isEmpty()) {
            throw new IllegalArgumentException("Las credenciales no son válidas.");
        }
        Cuenta cuenta = encontrada.get();
        if (!servicioContrasena.verificar(contraseniaPlana, cuenta.getHashContrasenia())) {
            throw new IllegalArgumentException("Las credenciales no son válidas.");
        }
        exigirCuentaActiva(cuenta);
        validarNegocioActivo(idNegocio);
        validarPerfil(idNegocio, idCliente);
        if (vinculoRepositorio.existeVinculo(cuenta.getId(), idNegocio)) {
            throw new IllegalStateException("La cuenta ya está vinculada a este negocio.");
        }
        VinculoCuentaNegocio vinculo =
                new VinculoCuentaNegocio(0, cuenta.getId(), idNegocio, idCliente, RolUsuario.CLIENTE);
        vinculoRepositorio.guardar(vinculo);
        return emitirSesion(vinculo);
    }

    SesionResponseDTO cambiarNegocio(Principal solicitante, long idNegocio) {
        if (solicitante == null) {
            throw new IllegalArgumentException("Se requiere autenticación para cambiar de negocio.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio solicitado no es válido.");
        }
        Optional<VinculoCuentaNegocio> vinculo =
                vinculoRepositorio.buscarPorCuentaYNegocio(solicitante.idCuenta(), idNegocio);
        if (vinculo.isEmpty()) {
            throw new IllegalStateException("La cuenta no tiene acceso al negocio solicitado.");
        }
        validarNegocioActivo(idNegocio);
        exigirVinculoActivo(vinculo.get());
        return emitirSesion(vinculo.get());
    }

    List<VinculoResponseDTO> listarNegocios(Principal solicitante) {
        if (solicitante == null) {
            throw new IllegalArgumentException("Se requiere autenticación para listar los negocios.");
        }
        List<VinculoResponseDTO> lista = new ArrayList<>();
        for (VinculoCuentaNegocio vinculo : vinculoRepositorio.listarPorCuenta(solicitante.idCuenta())) {
            String nombre = null;
            String subdominio = null;
            Optional<Negocio> negocio = negocioRepositorio.buscarPorId(vinculo.getIdNegocio());
            if (negocio.isPresent()) {
                nombre = negocio.get().getNombre();
                subdominio = negocio.get().getSubdominio();
            }
            lista.add(new VinculoResponseDTO(vinculo.getIdNegocio(), nombre, subdominio,
                    vinculo.getIdCliente(), vinculo.getRol().name(), vinculo.getEstado().name()));
        }
        return lista;
    }

    private void validarPerfil(long idNegocio, int idCliente) {
        if (clienteRepositorio.buscarPorId(idNegocio, idCliente).isEmpty()) {
            throw new IllegalArgumentException("No existe el cliente vinculado a la cuenta en este negocio.");
        }
    }

    private void validarNegocioActivo(long idNegocio) {
        Optional<Negocio> negocio = negocioRepositorio.buscarPorId(idNegocio);
        if (negocio.isEmpty() || !negocio.get().isActivo() || negocio.get().getFechaBaja() != null) {
            throw new IllegalStateException("El negocio solicitado no está activo.");
        }
    }

    private void exigirCuentaActiva(Cuenta cuenta) {
        if (!cuenta.estaActiva()) {
            throw new IllegalStateException("La cuenta está bloqueada o dada de baja.");
        }
    }

    private void exigirVinculoActivo(VinculoCuentaNegocio vinculo) {
        if (!vinculo.estaActivo()) {
            throw new IllegalStateException("El acceso a este negocio está bloqueado o dado de baja.");
        }
    }

    private RolUsuario resolverRol(String rolTexto, Principal solicitante, String tokenSetup) {
        if (rolTexto == null || rolTexto.trim().isEmpty()) {
            return RolUsuario.CLIENTE;
        }
        RolUsuario rol = RolUsuario.desdeTexto(rolTexto);
        if (rol == RolUsuario.CLIENTE) {
            return rol;
        }
        if (solicitante != null && solicitante.esAdmin()) {
            return rol;
        }
        if (tokenSetupValido(tokenSetup)) {
            return rol;
        }
        throw new IllegalStateException("Solo un administrador puede crear cuentas administradoras.");
    }

    private boolean tokenSetupValido(String tokenSetup) {
        if (tokenBootstrap == null || tokenSetup == null || tokenSetup.isEmpty()) {
            return false;
        }
        byte[] esperado = tokenBootstrap.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] recibido = tokenSetup.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(esperado, recibido);
    }

    private SesionResponseDTO emitirSesion(VinculoCuentaNegocio vinculo) {
        String token = proveedorToken.emitir(vinculo.getIdCuenta(), vinculo.getIdCliente(),
                vinculo.getIdNegocio(), vinculo.getRol().name());
        long expiracion = java.time.Instant.now().getEpochSecond() + proveedorToken.segundosDeVida();
        return new SesionResponseDTO(token, vinculo.getRol().name(), vinculo.getIdCuenta(),
                vinculo.getIdCliente(), vinculo.getIdNegocio(), expiracion);
    }
}
