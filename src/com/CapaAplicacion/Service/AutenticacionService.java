package com.CapaAplicacion.Service;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
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
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.OperacionTransaccional;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Mapper.ClienteMapper;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaDominio.Entidades.VinculoCuentaNegocio;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;

import java.sql.Connection;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AutenticacionService {
    private static final String HASH_FALSO_PARA_MEDICION_CONSTANTE =
            "pbkdf2$210000$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final ICuentaRepositorio cuentaRepositorio;
    private final IClienteRepositorio clienteRepositorio;
    private final ICuentaNegocioRepositorio vinculoRepositorio;
    private final INegocioRepository negocioRepositorio;
    private final IServicioContrasena servicioContrasena;
    private final IProveedorTokenAcceso proveedorToken;
    private final ITransaccion transaccion;
    private final IServiceExecutor executor;
    private final String tokenBootstrap;

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken,
                                ITransaccion transaccion) {
        this(cuentaRepositorio, clienteRepositorio, vinculoRepositorio, negocioRepositorio,
                servicioContrasena, proveedorToken, transaccion,
                RegistroExecutors.obtener(TipoServicio.AUTENTICACION));
    }

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken,
                                ITransaccion transaccion,
                                IServiceExecutor executor) {
        this(cuentaRepositorio, clienteRepositorio, vinculoRepositorio, negocioRepositorio,
                servicioContrasena, proveedorToken, transaccion, executor, null);
    }

    public AutenticacionService(ICuentaRepositorio cuentaRepositorio,
                                IClienteRepositorio clienteRepositorio,
                                ICuentaNegocioRepositorio vinculoRepositorio,
                                INegocioRepository negocioRepositorio,
                                IServicioContrasena servicioContrasena,
                                IProveedorTokenAcceso proveedorToken,
                                ITransaccion transaccion,
                                IServiceExecutor executor,
                                String tokenBootstrap) {
        if (cuentaRepositorio == null || clienteRepositorio == null || vinculoRepositorio == null
                || negocioRepositorio == null
                || servicioContrasena == null || proveedorToken == null
                || transaccion == null || executor == null) {
            throw new IllegalArgumentException("Las dependencias de autenticación son obligatorias.");
        }
        this.cuentaRepositorio = cuentaRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.vinculoRepositorio = vinculoRepositorio;
        this.negocioRepositorio = negocioRepositorio;
        this.servicioContrasena = servicioContrasena;
        this.proveedorToken = proveedorToken;
        this.transaccion = transaccion;
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
                                                                     final String dniCliente,
                                                                     final String nombreCliente,
                                                                     final String apellidoCliente,
                                                                     final LocalDate fechaNacimientoCliente,
                                                                     final String contraseniaPlana) {
        return executor.ejecutar(new Callable<SesionResponseDTO>() {
            @Override
            public SesionResponseDTO call() {
                return vincularNegocio(solicitante, idNegocio, dniCliente, nombreCliente,
                        apellidoCliente, fechaNacimientoCliente, contraseniaPlana);
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
        validarNegocioActivo(idNegocio);
        RolUsuario rol = resolverRol(dto.rol(), solicitante, tokenSetup);
        String correo = dto.correoElectronico().trim().toLowerCase();
        String dni = dto.dniCliente().trim();
        Optional<Cuenta> existente = cuentaRepositorio.buscarPorCorreo(correo);
        if (existente.isPresent()) {
            exigirCuentaActiva(existente.get());
            exigirCuentaDelSolicitante(existente.get(), solicitante);
        }
        String hashNuevo = existente.isEmpty() ? servicioContrasena.generarHash(dto.contraseniaPlana()) : null;
        AtomicReference<VinculoCuentaNegocio> creado = new AtomicReference<>();
        ejecutarTransaccion(new OperacionTransaccional() {
            @Override
            public void ejecutar(Connection conexion) {
                Cuenta cuenta;
                Optional<Cuenta> porCorreo = cuentaRepositorio.buscarPorCorreo(conexion, correo);
                if (porCorreo.isPresent()) {
                    cuenta = porCorreo.get();
                    exigirCuentaActiva(cuenta);
                    if (!servicioContrasena.verificar(dto.contraseniaPlana(), cuenta.getHashContrasenia())) {
                        throw new IllegalArgumentException("Las credenciales no son válidas.");
                    }
                    exigirCuentaDelSolicitante(cuenta, solicitante);
                } else {
                    String hash = hashNuevo != null ? hashNuevo
                            : servicioContrasena.generarHash(dto.contraseniaPlana());
                    cuenta = new Cuenta(0, dto.nombreCuenta(), correo, hash);
                    cuentaRepositorio.guardar(conexion, cuenta);
                    if (cuenta.getId() <= 0) {
                        Optional<Cuenta> creada = cuentaRepositorio.buscarPorCorreo(conexion, correo);
                        if (creada.isEmpty()) {
                            throw new IllegalStateException("No se pudo crear la cuenta.");
                        }
                        cuenta = creada.get();
                    }
                }
                int idCliente;
                Optional<Cliente> clienteExistente =
                        clienteRepositorio.buscarPorDniParaActualizar(conexion, idNegocio, dni);
                if (clienteExistente.isPresent()) {
                    Cliente cliente = clienteExistente.get();
                    if (!cliente.isActivo()) {
                        throw new IllegalStateException("El perfil de cliente está dado de baja.");
                    }
                    exigirIdentidadCoincidente(cliente, dto);
                    Optional<VinculoCuentaNegocio> vinculoDelCliente =
                            vinculoRepositorio.buscarPorNegocioYCliente(conexion, idNegocio, cliente.getId());
                    if (vinculoDelCliente.isPresent()
                            && vinculoDelCliente.get().getIdCuenta() != cuenta.getId()) {
                        throw new IllegalStateException(
                                "El perfil de cliente ya está vinculado a otra cuenta.");
                    }
                    idCliente = cliente.getId();
                } else {
                    idCliente = crearCliente(conexion, idNegocio, datosClienteDesdeRegistro(dto), dni);
                }
                VinculoCuentaNegocio vinculo =
                        new VinculoCuentaNegocio(cuenta.getId(), idNegocio, idCliente, rol);
                vinculoRepositorio.guardar(conexion, vinculo);
                creado.set(vinculo);
            }
        });
        VinculoCuentaNegocio vinculo = creado.get();
        if (vinculo == null) {
            throw new IllegalStateException("No se pudo completar el registro.");
        }
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

    SesionResponseDTO vincularNegocio(Principal solicitante, long idNegocio, String dniCliente,
                                      String nombreCliente, String apellidoCliente,
                                      LocalDate fechaNacimientoCliente, String contraseniaPlana) {
        if (solicitante == null) {
            throw new IllegalArgumentException("Se requiere autenticación para vincular un negocio.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio a vincular no es válido.");
        }
        if (dniCliente == null || dniCliente.trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI del perfil de cliente es obligatorio.");
        }
        validarDatosIdentidad(nombreCliente, apellidoCliente, fechaNacimientoCliente);
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
        String dni = dniCliente.trim();
        AtomicReference<VinculoCuentaNegocio> creado = new AtomicReference<>();
        ejecutarTransaccion(new OperacionTransaccional() {
            @Override
            public void ejecutar(Connection conexion) {
                Optional<Cliente> encontrado =
                        clienteRepositorio.buscarPorDniParaActualizar(conexion, idNegocio, dni);
                if (encontrado.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No existe el cliente vinculado a la cuenta en este negocio.");
                }
                Cliente cliente = encontrado.get();
                if (!cliente.isActivo()) {
                    throw new IllegalStateException("El perfil de cliente está dado de baja.");
                }
                exigirIdentidadCoincidente(cliente, nombreCliente, apellidoCliente, fechaNacimientoCliente);
                Optional<VinculoCuentaNegocio> vinculoDelCliente =
                        vinculoRepositorio.buscarPorNegocioYCliente(conexion, idNegocio, cliente.getId());
                if (vinculoDelCliente.isPresent()
                        && vinculoDelCliente.get().getIdCuenta() != cuenta.getId()) {
                    throw new IllegalStateException(
                            "El perfil de cliente ya está vinculado a otra cuenta.");
                }
                VinculoCuentaNegocio vinculo =
                        new VinculoCuentaNegocio(cuenta.getId(), idNegocio, cliente.getId(), RolUsuario.CLIENTE);
                vinculoRepositorio.guardar(conexion, vinculo);
                creado.set(vinculo);
            }
        });
        VinculoCuentaNegocio vinculo = creado.get();
        if (vinculo == null) {
            throw new IllegalStateException("No se pudo completar la vinculación.");
        }
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
            if (!vinculo.estaActivo()) {
                continue;
            }
            Optional<Negocio> negocio = negocioRepositorio.buscarPorId(vinculo.getIdNegocio());
            if (negocio.isEmpty() || !negocio.get().isActivo() || negocio.get().getFechaBaja() != null) {
                continue;
            }
            lista.add(new VinculoResponseDTO(vinculo.getIdNegocio(), negocio.get().getNombre(),
                    negocio.get().getSubdominio(), vinculo.getIdCliente(), vinculo.getRol().name(),
                    vinculo.getEstado().name()));
        }
        return lista;
    }

    private ClienteRequestDTO datosClienteDesdeRegistro(CuentaRegistroDTO dto) {
        try {
            return new ClienteRequestDTO(dto.dniCliente(), null, dto.nombreCliente(),
                    dto.apellidoCliente(), dto.direccionCliente(), dto.fechaNacimientoCliente(),
                    dto.localidadCliente());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "El DNI no está registrado en este negocio. Para crear el perfil de cliente complete "
                    + "nombre, apellido, fecha de nacimiento, dirección y localidad. " + error.getMessage());
        }
    }

    private int crearCliente(Connection conexion, long idNegocio, ClienteRequestDTO datosCliente, String dni) {
        Cliente cliente = ClienteMapper.toEntidad(0, datosCliente);
        cliente.setIdNegocio(idNegocio);
        clienteRepositorio.guardar(conexion, cliente);
        if (cliente.getId() <= 0) {
            Optional<Cliente> creado = clienteRepositorio.buscarPorDni(idNegocio, dni);
            if (creado.isEmpty()) {
                throw new IllegalStateException("No se pudo crear el cliente.");
            }
            cliente = creado.get();
        }
        return cliente.getId();
    }

    private void exigirIdentidadCoincidente(Cliente cliente, CuentaRegistroDTO dto) {
        exigirIdentidadCoincidente(cliente, dto.nombreCliente(), dto.apellidoCliente(),
                dto.fechaNacimientoCliente());
    }

    private void exigirIdentidadCoincidente(Cliente cliente, String nombre, String apellido,
                                            LocalDate fechaNacimiento) {
        validarDatosIdentidad(nombre, apellido, fechaNacimiento);
        boolean coincide = normalizar(nombre).equals(normalizar(cliente.getNombre()))
                && normalizar(apellido).equals(normalizar(cliente.getApellido()))
                && fechaNacimiento.equals(cliente.getFechaNacimiento());
        if (!coincide) {
            throw new IllegalStateException("Los datos de identidad no coinciden con el perfil de cliente.");
        }
    }

    private void validarDatosIdentidad(String nombre, String apellido, LocalDate fechaNacimiento) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Para vincular un perfil existente debe informar el nombre del titular.");
        }
        if (apellido == null || apellido.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Para vincular un perfil existente debe informar el apellido del titular.");
        }
        if (fechaNacimiento == null) {
            throw new IllegalArgumentException(
                    "Para vincular un perfil existente debe informar la fecha de nacimiento del titular.");
        }
        if (fechaNacimiento.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser una fecha futura.");
        }
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void exigirCuentaDelSolicitante(Cuenta cuenta, Principal solicitante) {
        if (solicitante != null && solicitante.idCuenta() != cuenta.getId()) {
            throw new IllegalStateException(
                    "El correo electrónico pertenece a otra cuenta. Inicie sesión con esa cuenta.");
        }
    }

    private void ejecutarTransaccion(OperacionTransaccional operacion) {
        try {
            transaccion.ejecutarTransaccion(operacion);
        } catch (RuntimeException error) {
            Throwable causa = error;
            while (causa != null) {
                if (causa instanceof IllegalArgumentException || causa instanceof IllegalStateException) {
                    if (causa instanceof RuntimeException runtime && causa != error) {
                        throw runtime;
                    }
                }
                causa = causa.getCause();
            }
            throw error;
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
