package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.CapaAplicacion.DTOentrada.CuentaRegistroDTO;
import com.CapaAplicacion.DTOsalida.SesionResponseDTO;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
import com.CapaInfraestructura.Implementacion.AdaptadorContrasena;
import com.CapaInfraestructura.Implementacion.DatabaseConnection;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaInfraestructura.Implementacion.JDBCTransaccion;
import com.CapaInfraestructura.Implementacion.MySqlNegocioRepository;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCliente;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuenta;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuentaNegocio;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioProducto;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioVenta;
import com.CapaInfraestructura.Implementacion.ProveedorTokenAcceso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ComprasMultiNegocioMySqlTest {
    private static final String CONTRASENIA = "ClaveSegura123";

    @Test
    void compraEnDosNegociosSinMezclarDatos() throws Exception {
        assumeTrue(hayBaseDeDatos(), "Sin MySQL local disponible: prueba de integración omitida.");
        List<StockProducto> productos = buscarDosProductosDeNegociosDistintos();
        assumeTrue(productos.size() == 2,
                "Se necesitan dos productos con stock en negocios distintos: prueba omitida.");

        StockProducto productoA = productos.get(0);
        StockProducto productoB = productos.get(1);

        MySqlRepositorioCliente clienteRepositorio = new MySqlRepositorioCliente();
        MySqlRepositorioProducto productoRepositorio = new MySqlRepositorioProducto();
        MySqlRepositorioVenta ventaRepositorio = new MySqlRepositorioVenta();
        MySqlNegocioRepository negocioRepositorio = new MySqlNegocioRepository();
        ProveedorTokenAcceso proveedor = new ProveedorTokenAcceso();
        AutenticacionService autenticacion = new AutenticacionService(
                new MySqlRepositorioCuenta(),
                clienteRepositorio,
                new MySqlRepositorioCuentaNegocio(),
                negocioRepositorio,
                new AdaptadorContrasena(),
                proveedor,
                new JDBCTransaccion(),
                new EjecutorDirecto());
        VentaService ventas = new VentaService(
                new JDBCTransaccion(),
                ventaRepositorio,
                productoRepositorio,
                clienteRepositorio,
                new InMemoryEventBus(),
                null,
                null,
                new ValidadorNegocioService(negocioRepositorio));

        String correo = "it.compra." + UUID.randomUUID() + "@ecommerce-test.local";
        String dni = generarDniLibre(clienteRepositorio, productoA.idNegocio(), productoB.idNegocio());
        CuentaRegistroDTO registro = new CuentaRegistroDTO(
                "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                correo, CONTRASENIA, dni, "CLIENTE",
                "Compra", "Multitenant", LocalDate.of(1993, 7, 15),
                "Calle Compra 123", "Localidad Test");

        long idCuenta = -1;
        int idClienteA = -1;
        int idClienteB = -1;
        Integer idVentaA = null;
        Integer idVentaB = null;
        try {
            SesionResponseDTO sesionA = autenticacion.registrar(productoA.idNegocio(), registro, null, null);
            idCuenta = sesionA.idCuenta();
            idClienteA = sesionA.idCliente();
            SesionResponseDTO sesionB = autenticacion.registrar(productoB.idNegocio(), registro, null, null);
            idClienteB = sesionB.idCliente();

            assertEquals(idCuenta, sesionB.idCuenta());
            assertTrue(proveedor.validar(sesionA.token()).isPresent());
            proveedor.revocar(sesionA.token());
            assertTrue(proveedor.validar(sesionA.token()).isEmpty(),
                    "El token revocado no debe validar.");
            assertTrue(proveedor.validar(sesionB.token()).isPresent(),
                    "Revocar el token de A no debe afectar al de B.");
            Producto productoParaA = productoRepositorio
                    .buscarPorId(productoA.idNegocio(), productoA.idProducto()).orElseThrow();
            Producto productoParaB = productoRepositorio
                    .buscarPorId(productoB.idNegocio(), productoB.idProducto()).orElseThrow();

            Carrito carritoA = new Carrito(idClienteA);
            carritoA.agregarItem(new ItemCarrito(productoParaA, 1));
            ventas.crearVentaAsync(productoA.idNegocio(), carritoA, "EFECTIVO", null)
                    .get(15, TimeUnit.SECONDS);

            Carrito carritoB = new Carrito(idClienteB);
            carritoB.agregarItem(new ItemCarrito(productoParaB, 1));
            ventas.crearVentaAsync(productoB.idNegocio(), carritoB, "EFECTIVO", null)
                    .get(15, TimeUnit.SECONDS);

            idVentaA = ultimaVenta(productoA.idNegocio(), idClienteA);
            idVentaB = ultimaVenta(productoB.idNegocio(), idClienteB);
            assertNotNull(idVentaA);
            assertNotNull(idVentaB);

            assertTrue(ventaRepositorio.buscarPorId(productoA.idNegocio(), idVentaA).isPresent());
            assertTrue(ventaRepositorio.buscarPorId(productoB.idNegocio(), idVentaB).isPresent());
            assertTrue(ventaRepositorio.buscarPorId(productoA.idNegocio(), idVentaB).isEmpty(),
                    "La venta de B no debe ser visible desde A.");
            assertTrue(ventaRepositorio.buscarPorId(productoB.idNegocio(), idVentaA).isEmpty(),
                    "La venta de A no debe ser visible desde B.");

            assertEquals(productoA.stockFisico() - 1, productoRepositorio
                    .buscarPorId(productoA.idNegocio(), productoA.idProducto()).orElseThrow()
                    .getStockFisico());
            assertEquals(productoB.stockFisico() - 1, productoRepositorio
                    .buscarPorId(productoB.idNegocio(), productoB.idProducto()).orElseThrow()
                    .getStockFisico());
        } finally {
            eliminarVentasDeCliente(productoA.idNegocio(), idClienteA);
            eliminarVentasDeCliente(productoB.idNegocio(), idClienteB);
            if (idCuenta > 0) {
                limpiarRevocaciones(idCuenta);
                limpiarCuenta(idCuenta);
            }
            limpiarClientePorDni(productoA.idNegocio(), dni);
            limpiarClientePorDni(productoB.idNegocio(), dni);
            restaurarStock(productoA);
            restaurarStock(productoB);
        }
    }

    private record StockProducto(long idNegocio, int idProducto, int stockFisico) {
    }

    private static List<StockProducto> buscarDosProductosDeNegociosDistintos() throws SQLException {
        String sql = "SELECT p.id, p.id_negocio, p.stock_fisico FROM productos p "
                + "JOIN negocio n ON n.id = p.id_negocio "
                + "WHERE n.activo = 1 AND n.fecha_baja IS NULL AND p.activo = 1 "
                + "AND p.stock_fisico > p.stock_reservado AND p.stock_fisico > 1 "
                + "ORDER BY p.id_negocio, p.id";
        List<StockProducto> todos = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                todos.add(new StockProducto(result.getLong("id_negocio"),
                        result.getInt("id"), result.getInt("stock_fisico")));
            }
        }
        if (todos.isEmpty()) {
            return List.of();
        }
        StockProducto primero = todos.get(0);
        for (StockProducto candidato : todos) {
            if (candidato.idNegocio() != primero.idNegocio()) {
                return List.of(primero, candidato);
            }
        }
        return List.of();
    }

    private static String generarDniLibre(MySqlRepositorioCliente repositorio,
                                          long idNegocioA, long idNegocioB) throws SQLException {
        for (int intento = 0; intento < 20; intento++) {
            String candidato = "8" + String.format("%07d",
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000_000));
            if (repositorio.buscarPorDni(idNegocioA, candidato).isEmpty()
                    && repositorio.buscarPorDni(idNegocioB, candidato).isEmpty()) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un DNI libre para la prueba.");
    }

    private static int ultimaVenta(long idNegocio, int idCliente) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM ventas WHERE id_negocio = ? AND id_cliente = ? "
                             + "ORDER BY id DESC LIMIT 1")) {
            statement.setLong(1, idNegocio);
            statement.setInt(2, idCliente);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("id") : -1;
            }
        }
    }

    private static void eliminarVentasDeCliente(long idNegocio, int idCliente) {
        if (idNegocio <= 0 || idCliente <= 0) {
            return;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM ventas WHERE id_negocio = ? AND id_cliente = ?")) {
            statement.setLong(1, idNegocio);
            statement.setInt(2, idCliente);
            statement.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudieron limpiar las ventas de prueba: " + error.getMessage());
        }
    }

    private static void limpiarRevocaciones(long idCuenta) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM tokens_revocados WHERE id_cuenta = ?")) {
            statement.setLong(1, idCuenta);
            statement.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudieron limpiar las revocaciones de prueba: " + error.getMessage());
        }
    }

    private static void limpiarCuenta(long idCuenta) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement vinculos = connection.prepareStatement(
                    "DELETE FROM cuentas_negocio WHERE id_cuenta = ?")) {
                vinculos.setLong(1, idCuenta);
                vinculos.executeUpdate();
            }
            try (PreparedStatement cuenta = connection.prepareStatement(
                    "DELETE FROM cuentas WHERE id = ?")) {
                cuenta.setLong(1, idCuenta);
                cuenta.executeUpdate();
            }
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar la cuenta de prueba: " + error.getMessage());
        }
    }

    private static void limpiarClientePorDni(long idNegocio, String dni) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM clientes WHERE dni = ? AND id_negocio = ?")) {
            statement.setString(1, dni);
            statement.setLong(2, idNegocio);
            statement.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar el cliente de prueba: " + error.getMessage());
        }
    }

    private static void restaurarStock(StockProducto producto) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE productos SET stock_fisico = ? WHERE id = ? AND id_negocio = ?")) {
            statement.setInt(1, producto.stockFisico());
            statement.setInt(2, producto.idProducto());
            statement.setLong(3, producto.idNegocio());
            statement.executeUpdate();
        } catch (SQLException error) {
            System.err.println("No se pudo restaurar el stock de prueba: " + error.getMessage());
        }
    }

    private static boolean hayBaseDeDatos() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return connection != null && connection.isValid(2);
        } catch (Throwable error) {
            return false;
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
            } catch (Exception error) {
                CompletableFuture<T> fallido = new CompletableFuture<>();
                fallido.completeExceptionally(error);
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
