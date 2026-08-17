package com.CapaExterna;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Service.AuditoriaService;
import com.CapaAplicacion.Service.ClienteService;
import com.CapaAplicacion.Service.CuentaService;
import com.CapaAplicacion.Service.ProductoService;
import com.CapaAplicacion.Service.RecomendacionService;
import com.CapaAplicacion.Service.StockService;
import com.CapaAplicacion.Service.VentaService;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.Producto;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaInfraestructura.Implementacion.RepositorioAuditoria;
import com.CapaInfraestructura.Implementacion.RepositorioCliente;
import com.CapaInfraestructura.Implementacion.RepositorioCuenta;
import com.CapaInfraestructura.Implementacion.RepositorioProducto;
import com.CapaInfraestructura.Implementacion.RepositorioVentas;
import com.CapaPresentacion.Controllers.AuditoriaController;
import com.CapaPresentacion.Controllers.ClienteController;
import com.CapaPresentacion.Controllers.CuentaController;
import com.CapaPresentacion.Controllers.ProductoController;
import com.CapaPresentacion.Controllers.RecomendacionController;
import com.CapaPresentacion.Controllers.StockController;
import com.CapaPresentacion.Controllers.VentaController;
import com.sun.net.httpserver.HttpServer;

public class ServerMain {

    public static void main(String[] args) {
        try {
            System.out.println("==================================================");
            System.out.println("🚀 INICIANDO SERVIDOR HTTP E-COMMERCE EN PUERTO 8080");
            System.out.println("==================================================\n");

         
            IProductoRepository productoRepo = new RepositorioProducto();
            IVentaRepository ventaRepo = new RepositorioVentas();
            IClienteRepositorio clienteRepo = new RepositorioCliente();
            ICuentaRepositorio cuentaRepo = new RepositorioCuenta();
            IAuditoriaRepository auditoriaRepo = new RepositorioAuditoria();


            EventBus eventBus = new InMemoryEventBus();

            prepopulateData(productoRepo);

          
            ProductoService productoService = new ProductoService(productoRepo, eventBus);
            ClienteService clienteService = new ClienteService(clienteRepo);
            CuentaService cuentaService = new CuentaService(cuentaRepo);
            VentaService ventaService = new VentaService(ventaRepo, productoRepo, cuentaRepo, eventBus);
            AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepo, eventBus);
            StockService stockService = new StockService();
            RecomendacionService recomendacionService = new RecomendacionService();

 
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

   
            server.createContext("/api/productos", new ProductoController(productoService));
            server.createContext("/api/clientes", new ClienteController(clienteService));
            server.createContext("/api/cuentas", new CuentaController(cuentaService));
            server.createContext("/api/ventas", new VentaController(ventaService, productoRepo));
            server.createContext("/api/auditoria", new AuditoriaController(auditoriaService));
            server.createContext("/api/stock", new StockController(stockService, productoRepo));
            server.createContext("/api/recomendaciones", new RecomendacionController(recomendacionService, productoRepo));

     
            server.setExecutor(Executors.newCachedThreadPool());


            server.start();
            System.out.println("✅ Servidor HTTP iniciado correctamente en http://localhost:8080/");
            System.out.println("Rutas disponibles:");
            System.out.println("  - Productos: GET/POST/PUT/DELETE /api/productos");
            System.out.println("  - Clientes: GET/POST/PUT/DELETE /api/clientes");
            System.out.println("  - Cuentas: GET/POST/PUT/DELETE /api/cuentas, POST /api/cuentas/transferir, GET /api/cuentas/buscarPorCorreo");
            System.out.println("  - Ventas: POST /api/ventas/crear, GET /api/ventas/{id}, POST /api/ventas/{id}/pago, POST /api/ventas/{id}/cancelar");
            System.out.println("\nPresione Ctrl+C para detener el servidor.");

        } catch (IOException e) {
            System.err.println("❌ Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void prepopulateData(IProductoRepository productoRepo) {
        try {

            productoRepo.guardar(new Producto("Laptop Gamer", BigDecimal.valueOf(1000.0), 10, 0, 1, CategoriaProducto.ELECTRONICA));
            productoRepo.guardar(new Producto("Mouse Vertical", BigDecimal.valueOf(50.0), 15, 0, 2, CategoriaProducto.ELECTRONICA));
            productoRepo.guardar(new Producto("Teclado Mecanico", BigDecimal.valueOf(120.0), 8, 0, 3, CategoriaProducto.ELECTRONICA));

            System.out.println("📦 Catálogo inicial cargado con éxito.");
        } catch (Exception e) {
            System.err.println("⚠️ Advertencia al precargar datos: " + e.getMessage());
        }
    }
}

