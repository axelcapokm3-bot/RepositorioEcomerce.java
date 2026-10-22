package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.CapaAplicacion.DTOentrada.CuentaIngresoDTO;
import com.CapaAplicacion.DTOentrada.CuentaRegistroDTO;
import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.DTOsalida.VinculoResponseDTO;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.Cliente;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaInfraestructura.Implementacion.AdaptadorContrasena;
import com.CapaInfraestructura.Implementacion.DatabaseConnection;
import com.CapaInfraestructura.Implementacion.JDBCTransaccion;
import com.CapaInfraestructura.Implementacion.MySqlNegocioRepository;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCliente;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuenta;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuentaNegocio;
import com.CapaInfraestructura.Implementacion.ProveedorTokenAcceso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Prueba de integración contra el MySQL real (repositorios de infraestructura,
 * no dobles de prueba). Se omite automáticamente si no hay base disponible.
 * Los datos que crea son efímeros: se eliminan en el bloque finally.
 */
class IdentidadGlobalMySqlTest {
    private static final String CONTRASENIA = "ClaveSegura123";

    @Test
    void registroIngresoYAislamientoContraMySql() throws Exception {
        assumeTrue(hayBaseDeDatos(), "Sin MySQL local disponible: prueba de integración omitida.");
        final ClienteLibre libre = buscarNegocioYClienteLibres();
        assumeTrue(libre != null,
                "No hay clientes sin vincular en negocios activos: prueba de integración omitida.");
        final long idNegocio = libre.idNegocio();
        final int idCliente = libre.idCliente();

        final ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso();
        final AutenticacionService servicio = new AutenticacionService(
                new MySqlRepositorioCuenta(),
                new MySqlRepositorioCliente(),
                new MySqlRepositorioCuentaNegocio(),
                new MySqlNegocioRepository(),
                new AdaptadorContrasena(),
                proveedor,
                new JDBCTransaccion(),
                new EjecutorDirecto());

        final String correo = "it." + UUID.randomUUID() + "@ecommerce-test.local";
        final String nombreCuenta = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final CuentaRegistroDTO registro =
                new CuentaRegistroDTO(nombreCuenta, correo, CONTRASENIA, libre.dni(), "CLIENTE",
                        libre.nombre(), libre.apellido(), libre.fechaNacimiento(),
                        "Calle Test 123", "Localidad Test");

        long idCuenta = -1;
        try {
            SesionResponseDTO sesion = servicio.registrar(idNegocio, registro, null, null);
            idCuenta = sesion.idCuenta();
            assertTrue(idCuenta > 0, "El registro debe persistir la cuenta en MySQL.");
            assertNotNull(sesion.token());
            assertEquals("CLIENTE", sesion.rol());
            assertEquals(idCliente, sesion.idCliente());
            assertEquals(idNegocio, sesion.idNegocio());

            assertTrue(estaActivaLaCuenta(idCuenta), "cuentas.activo debe quedar en 1.");
            assertTrue(estaActivoElVinculo(idCuenta, idNegocio), "cuentas_negocio.activo debe quedar en 1.");

            java.util.Optional<Principal> principalValidado = proveedor.validar(sesion.token());
            assertTrue(principalValidado.isPresent(), "El token emitido debe validarse con el mismo secreto.");
            assertEquals(idCuenta, principalValidado.get().idCuenta());
            assertEquals(idNegocio, principalValidado.get().idNegocio());

            SesionResponseDTO ingreso =
                    servicio.ingresar(idNegocio, new CuentaIngresoDTO(correo, CONTRASENIA));
            assertEquals(idCuenta, ingreso.idCuenta());
            assertEquals(idNegocio, ingreso.idNegocio());

            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    servicio.ingresar(idNegocio, new CuentaIngresoDTO(correo, "ClaveIncorrecta999"));
                }
            });

            long otroNegocio = buscarOtroNegocioActivo(idNegocio);
            assumeTrue(otroNegocio > 0, "Se necesita un segundo negocio activo para probar aislamiento.");
            final long negocioSinVinculo = otroNegocio;
            assertThrows(IllegalStateException.class, new Executable() {
                @Override
                public void execute() {
                    servicio.ingresar(negocioSinVinculo, new CuentaIngresoDTO(correo, CONTRASENIA));
                }
            });

            Principal principal = new Principal(idCuenta, idCliente, idNegocio, RolUsuario.CLIENTE);
            assertThrows(IllegalStateException.class, new Executable() {
                @Override
                public void execute() {
                    servicio.cambiarNegocio(principal, negocioSinVinculo);
                }
            });
            SesionResponseDTO cambio = servicio.cambiarNegocio(principal, idNegocio);
            assertEquals(idNegocio, cambio.idNegocio());

            assertThrows(IllegalStateException.class, new Executable() {
                @Override
                public void execute() {
                    servicio.registrar(idNegocio, registro, null, null);
                }
            });

            List<VinculoResponseDTO> vinculos = servicio.listarNegocios(principal);
            assertEquals(1, vinculos.size());
            assertEquals(idNegocio, vinculos.get(0).idNegocio());
            assertNotNull(vinculos.get(0).nombreNegocio(), "El nombre del negocio debe resolverse desde MySQL.");
        } finally {
            if (idCuenta > 0) {
                limpiarCuentaDePrueba(idCuenta);
            }
        }
    }

    @Test
    void registroNoReclamaPerfilLibreConIdentidadIncorrecta() throws Exception {
        assumeTrue(hayBaseDeDatos(), "Sin MySQL local disponible: prueba de integración omitida.");
        final ClienteLibre libre = buscarNegocioYClienteLibres();
        assumeTrue(libre != null,
                "No hay clientes sin vincular en negocios activos: prueba de integración omitida.");
        final long idNegocio = libre.idNegocio();
        final ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso();
        final AutenticacionService servicio = new AutenticacionService(
                new MySqlRepositorioCuenta(),
                new MySqlRepositorioCliente(),
                new MySqlRepositorioCuentaNegocio(),
                new MySqlNegocioRepository(),
                new AdaptadorContrasena(),
                proveedor,
                new JDBCTransaccion(),
                new EjecutorDirecto());

        final String correoAtaque = "it." + UUID.randomUUID() + "@ecommerce-test.local";
        final String nombreCuentaAtaque = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final CuentaRegistroDTO ataque = new CuentaRegistroDTO(nombreCuentaAtaque, correoAtaque, CONTRASENIA,
                libre.dni(), "CLIENTE", "Atacante", "Falso", LocalDate.of(1980, 1, 1),
                "Calle Falsa 123", "Sin Localidad");

        long idCuenta = -1;
        try {
            assertThrows(IllegalStateException.class, new Executable() {
                @Override
                public void execute() {
                    servicio.registrar(idNegocio, ataque, null, null);
                }
            });
            assertFalse(existeCuentaPorCorreo(correoAtaque),
                    "La transacción debe revertir la cuenta creada por el intento de reclamo.");
            assertFalse(existeVinculoDeCliente(idNegocio, libre.idCliente()),
                    "El perfil libre no debe quedar vinculado tras el intento.");

            final String correoLegitimo = "it." + UUID.randomUUID() + "@ecommerce-test.local";
            final String nombreCuenta = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            final CuentaRegistroDTO legitimo = new CuentaRegistroDTO(nombreCuenta, correoLegitimo, CONTRASENIA,
                    libre.dni(), "CLIENTE", libre.nombre(), libre.apellido(), libre.fechaNacimiento(),
                    "Calle Test 123", "Localidad Test");
            SesionResponseDTO sesion = servicio.registrar(idNegocio, legitimo, null, null);
            idCuenta = sesion.idCuenta();
            assertEquals(libre.idCliente(), sesion.idCliente());
            assertTrue(existeVinculoDeCliente(idNegocio, libre.idCliente()));
        } finally {
            if (idCuenta > 0) {
                limpiarCuentaDePrueba(idCuenta);
            }
            limpiarCuentaDePruebaPorCorreo(correoAtaque);
        }
    }

    @Test
    void registroCreaClienteNuevoEnMySql() throws Exception {
        assumeTrue(hayBaseDeDatos(), "Sin MySQL local disponible: prueba de integración omitida.");
        final long idNegocio = buscarNegocioActivo();
        assumeTrue(idNegocio > 0, "Se necesita un negocio activo para la prueba de integración.");

        final ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso();
        final MySqlRepositorioCliente repositorioClientes = new MySqlRepositorioCliente();
        final AutenticacionService servicio = new AutenticacionService(
                new MySqlRepositorioCuenta(),
                repositorioClientes,
                new MySqlRepositorioCuentaNegocio(),
                new MySqlNegocioRepository(),
                new AdaptadorContrasena(),
                proveedor,
                new JDBCTransaccion(),
                new EjecutorDirecto());

        final String dni = generarDniLibre(repositorioClientes, idNegocio);
        final String correo = "it." + UUID.randomUUID() + "@ecommerce-test.local";
        final String nombreCuenta = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final CuentaRegistroDTO registro = new CuentaRegistroDTO(nombreCuenta, correo, CONTRASENIA, dni, "CLIENTE",
                "Prueba", "Integracion", LocalDate.of(1990, 1, 1), "Calle Test 123", "Localidad Test");

        long idCuenta = -1;
        int idCliente = -1;
        try {
            SesionResponseDTO sesion = servicio.registrar(idNegocio, registro, null, null);
            idCuenta = sesion.idCuenta();
            idCliente = sesion.idCliente();
            assertTrue(idCuenta > 0, "El registro debe persistir la cuenta en MySQL.");
            assertTrue(idCliente > 0, "El registro debe crear el cliente en MySQL.");
            assertNotNull(sesion.token());

            java.util.Optional<Cliente> creado = repositorioClientes.buscarPorDni(idNegocio, dni);
            assertTrue(creado.isPresent(), "El cliente nuevo debe quedar en la tabla clientes.");
            assertEquals(idCliente, creado.get().getId());
            assertEquals(idNegocio, creado.get().getIdNegocio());
            assertEquals("Prueba", creado.get().getNombre());
            assertEquals("Integracion", creado.get().getApellido());
            assertEquals("Calle Test 123", creado.get().getDireccion());
            assertEquals("Localidad Test", creado.get().getLocalidad());

            SesionResponseDTO ingreso =
                    servicio.ingresar(idNegocio, new CuentaIngresoDTO(correo, CONTRASENIA));
            assertEquals(idCuenta, ingreso.idCuenta());
            assertEquals(idCliente, ingreso.idCliente());
            assertEquals(idNegocio, ingreso.idNegocio());
        } finally {
            if (idCuenta > 0) {
                limpiarCuentaDePrueba(idCuenta);
            } else {
                limpiarCuentaDePruebaPorCorreo(correo);
            }
            if (idCliente > 0) {
                limpiarClienteDePrueba(idCliente);
            } else {
                limpiarClienteDePruebaPorDni(idNegocio, dni);
            }
        }
    }

    @Test
    void tokenDejaDeSerValidoTrasCambiarElEstadoDeLaSesion() throws Exception {
        assumeTrue(hayBaseDeDatos(), "Sin MySQL local disponible: prueba de integración omitida.");
        final long idNegocio = buscarNegocioActivo();
        assumeTrue(idNegocio > 0, "Se necesita un negocio activo para la prueba de integración.");

        final MySqlRepositorioCliente repositorioClientes = new MySqlRepositorioCliente();
        final String dni = generarDniLibre(repositorioClientes, idNegocio);
        final String correo = "it." + UUID.randomUUID() + "@ecommerce-test.local";
        final String nombreCuenta = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final CuentaRegistroDTO registro = new CuentaRegistroDTO(
                nombreCuenta, correo, CONTRASENIA, dni, "CLIENTE",
                "Prueba", "Sesion", LocalDate.of(1990, 1, 1), "Calle Test 123", "Localidad Test");

        long idCuenta = -1;
        int idCliente = -1;
        try {
            final ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso();
            final AutenticacionService servicio = new AutenticacionService(
                    new MySqlRepositorioCuenta(),
                    repositorioClientes,
                    new MySqlRepositorioCuentaNegocio(),
                    new MySqlNegocioRepository(),
                    new AdaptadorContrasena(),
                    proveedor,
                    new JDBCTransaccion(),
                    new EjecutorDirecto());

            SesionResponseDTO sesion = servicio.registrar(idNegocio, registro, null, null);
            idCuenta = sesion.idCuenta();
            idCliente = sesion.idCliente();
            String token = sesion.token();
            assertTrue(proveedor.validar(token).isPresent());

            ejecutarUpdate("UPDATE cuentas_negocio SET rol_usuario = 'ADMIN' "
                    + "WHERE id_cuenta = ? AND id_negocio = ?", idCuenta, idNegocio);
            assertTrue(proveedor.validar(token).isEmpty(),
                    "Un cambio de rol debe invalidar el token anterior.");

            ejecutarUpdate("UPDATE cuentas_negocio SET rol_usuario = 'CLIENTE', activo = 0 "
                    + "WHERE id_cuenta = ? AND id_negocio = ?", idCuenta, idNegocio);
            assertTrue(proveedor.validar(token).isEmpty(),
                    "Un vínculo inactivo debe invalidar el token.");

            ejecutarUpdate("UPDATE cuentas_negocio SET activo = 1 "
                    + "WHERE id_cuenta = ? AND id_negocio = ?", idCuenta, idNegocio);
            ejecutarUpdate("UPDATE cuentas SET activo = 0 WHERE id = ?", idCuenta);
            assertTrue(proveedor.validar(token).isEmpty(),
                    "Una cuenta inactiva debe invalidar el token.");
        } finally {
            if (idCuenta > 0) {
                limpiarCuentaDePrueba(idCuenta);
            } else {
                limpiarCuentaDePruebaPorCorreo(correo);
            }
            if (idCliente > 0) {
                limpiarClienteDePrueba(idCliente);
            } else {
                limpiarClienteDePruebaPorDni(idNegocio, dni);
            }
        }
    }

    private static void ejecutarUpdate(String sql, Object... parametros) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                statement.setObject(i + 1, parametros[i]);
            }
            statement.executeUpdate();
        }
    }

    private static boolean hayBaseDeDatos() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return connection != null && connection.isValid(2);
        } catch (Throwable error) {
            return false;
        }
    }

    private record ClienteLibre(long idNegocio, int idCliente, String dni, String nombre,
                                String apellido, LocalDate fechaNacimiento) {}

    private static long buscarNegocioActivo() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM negocio WHERE activo = 1 AND fecha_baja IS NULL ORDER BY id LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong("id") : -1;
        }
    }

    private static String generarDniLibre(MySqlRepositorioCliente repositorio, long idNegocio) throws SQLException {
        for (int intento = 0; intento < 20; intento++) {
            String candidato = "9" + String.format("%07d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000_000));
            if (repositorio.buscarPorDni(idNegocio, candidato).isEmpty()) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un DNI libre para la prueba de integración.");
    }

    private static ClienteLibre buscarNegocioYClienteLibres() throws SQLException {
        String sql = "SELECT c.id AS id_cliente, c.dni AS dni, c.id_negocio, c.nombre, c.apellido, "
                + "c.fecha_nacimiento FROM clientes c "
                + "JOIN negocio n ON n.id = c.id_negocio "
                + "WHERE n.activo = 1 AND n.fecha_baja IS NULL AND c.activo = 1 "
                + "AND c.fecha_nacimiento IS NOT NULL AND c.nombre IS NOT NULL AND c.apellido IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM cuentas_negocio cn "
                + "WHERE cn.id_negocio = c.id_negocio AND cn.id_cliente = c.id) "
                + "LIMIT 1";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                return new ClienteLibre(result.getLong("id_negocio"), result.getInt("id_cliente"),
                        result.getString("dni"), result.getString("nombre"), result.getString("apellido"),
                        result.getDate("fecha_nacimiento").toLocalDate());
            }
        }
        return null;
    }

    private static long buscarOtroNegocioActivo(long idNegocio) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM negocio WHERE activo = 1 AND fecha_baja IS NULL AND id <> ? LIMIT 1")) {
            statement.setLong(1, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("id") : -1;
            }
        }
    }

    private static boolean estaActivaLaCuenta(long idCuenta) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT activo FROM cuentas WHERE id = ?")) {
            statement.setLong(1, idCuenta);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean("activo");
            }
        }
    }

    private static boolean existeCuentaPorCorreo(String correo) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM cuentas WHERE correo_electronico = ? LIMIT 1")) {
            statement.setString(1, correo);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean existeVinculoDeCliente(long idNegocio, int idCliente) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM cuentas_negocio WHERE id_negocio = ? AND id_cliente = ? LIMIT 1")) {
            statement.setLong(1, idNegocio);
            statement.setInt(2, idCliente);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean estaActivoElVinculo(long idCuenta, long idNegocio) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT activo FROM cuentas_negocio WHERE id_cuenta = ? AND id_negocio = ?")) {
            statement.setLong(1, idCuenta);
            statement.setLong(2, idNegocio);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean("activo");
            }
        }
    }

    private static void limpiarClienteDePrueba(int idCliente) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement borrarCliente = connection.prepareStatement(
                     "DELETE FROM clientes WHERE id = ?")) {
            borrarCliente.setInt(1, idCliente);
            borrarCliente.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar el cliente de prueba " + idCliente + ": " + error.getMessage());
        }
    }

    private static void limpiarCuentaDePruebaPorCorreo(String correo) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement borrarCuenta = connection.prepareStatement(
                     "DELETE FROM cuentas WHERE correo_electronico = ?")) {
            borrarCuenta.setString(1, correo);
            borrarCuenta.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar la cuenta de prueba " + correo + ": " + error.getMessage());
        }
    }

    private static void limpiarClienteDePruebaPorDni(long idNegocio, String dni) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement borrarCliente = connection.prepareStatement(
                     "DELETE FROM clientes WHERE dni = ? AND id_negocio = ?")) {
            borrarCliente.setString(1, dni);
            borrarCliente.setLong(2, idNegocio);
            borrarCliente.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar el cliente de prueba " + dni + ": " + error.getMessage());
        }
    }

    private static void limpiarCuentaDePrueba(long idCuenta) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement borrarVinculos = connection.prepareStatement(
                    "DELETE FROM cuentas_negocio WHERE id_cuenta = ?")) {
                borrarVinculos.setLong(1, idCuenta);
                borrarVinculos.executeUpdate();
            }
            try (PreparedStatement borrarCuenta = connection.prepareStatement(
                    "DELETE FROM cuentas WHERE id = ?")) {
                borrarCuenta.setLong(1, idCuenta);
                borrarCuenta.executeUpdate();
            }
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar la cuenta de prueba " + idCuenta + ": " + error.getMessage());
        }
    }

    private static final class EjecutorDirecto implements IServiceExecutor {
        @Override
        public CompletableFuture<Void> ejecutar(Runnable tarea) {
            tarea.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> ejecutar(Callable<T> tarea) {
            try {
                return CompletableFuture.completedFuture(tarea.call());
            } catch (Exception e) {
                CompletableFuture<T> fallido = new CompletableFuture<>();
                fallido.completeExceptionally(e);
                return fallido;
            }
        }

        @Override
        public int getColaActual() {
            return 0;
        }

        @Override
        public int getHiloActivos() {
            return 0;
        }

        @Override
        public int getHilosTotales() {
            return 1;
        }

        @Override
        public TipoServicio getTipo() {
            return TipoServicio.AUTENTICACION;
        }

        @Override
        public void shutdown() {
        }
    }
}
