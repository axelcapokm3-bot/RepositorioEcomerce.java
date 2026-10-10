package com.CapaExterna;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INotificadorEmail;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaInfraestructura.Implementacion.JDBCTransaccion;
import com.CapaInfraestructura.Implementacion.NotificadorEmailMock;
import com.CapaInfraestructura.Implementacion.NotificacionListener;
import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.IMetodoPagoRepositorio;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Service.AuditoriaService;
import com.CapaAplicacion.Service.ClienteService;
import com.CapaAplicacion.Service.MetodoPagoService;
import com.CapaAplicacion.Service.ProductoService;
import com.CapaAplicacion.Service.PagoService;
import com.CapaAplicacion.Service.RecomendacionService;
import com.CapaAplicacion.Service.StockService;
import com.CapaAplicacion.Service.StripeWebhookService;
import com.CapaAplicacion.Service.VentaService;
import com.CapaAplicacion.Service.WebhookService;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCliente;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioProducto;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioVenta;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioPago;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioAuditoria;
import com.CapaInfraestructura.Implementacion.MySqlNegocioRepository;
import com.CapaInfraestructura.Implementacion.PasarelaFactory;
import com.CapaInfraestructura.Implementacion.RepositorioMetodoPago;
import com.CapaInfraestructura.Implementacion.RedisCarritoRepository;
import redis.clients.jedis.JedisPool;
import com.CapaPresentacion.Controllers.AuditoriaController;
import com.CapaPresentacion.Controllers.ClienteController;
import com.CapaPresentacion.Controllers.MetodoPagoController;
import com.CapaPresentacion.Controllers.ProductoController;
import com.CapaPresentacion.Controllers.RecomendacionController;
import com.CapaPresentacion.Controllers.StockController;
import com.CapaPresentacion.Controllers.VentaController;
import com.CapaPresentacion.Controllers.WebhookController;
import com.CapaPresentacion.Controllers.StripeWebhookController;
import com.sun.net.httpserver.HttpServer;

public class ServerMain {

    public static void main(String[] args) {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    RegistroExecutors.cerrarTodos();
                }
            }, "executor-shutdown-hook"));

            System.out.println("==================================================");
            System.out.println("🚀 INICIANDO SERVIDOR HTTP E-COMMERCE EN PUERTO 8080");
            System.out.println("==================================================\n");

         
            IProductoRepository productoRepo = new MySqlRepositorioProducto();
            IVentaRepository ventaRepo = new MySqlRepositorioVenta();
            IPagoRepository pagoRepo = new MySqlRepositorioPago();
            INegocioRepository negocioRepo = new MySqlNegocioRepository();
            IClienteRepositorio clienteRepo = new MySqlRepositorioCliente();
            IMetodoPagoRepositorio metodoPagoRepo = new RepositorioMetodoPago();
            IAuditoriaRepository auditoriaRepo = new MySqlRepositorioAuditoria();
            ICarritoRepository carritoRepo = new RedisCarritoRepository(new JedisPool("localhost", 6379));


            ITransaccion transaccion = new JDBCTransaccion();
            EventBus eventBus = new InMemoryEventBus();
            INotificadorEmail notificador = new NotificadorEmailMock();
            new NotificacionListener(eventBus, notificador);

            PasarelaFactory.inicializar(negocioRepo);
            PagoService pagoService = new PagoService(pagoRepo, ventaRepo, eventBus);

          
            ProductoService productoService = new ProductoService(productoRepo, eventBus);
            ClienteService clienteService = new ClienteService(clienteRepo);
            MetodoPagoService metodoPagoService = new MetodoPagoService(metodoPagoRepo);
                VentaService ventaService = new VentaService(transaccion, ventaRepo, productoRepo, clienteRepo,
                    metodoPagoRepo, eventBus, carritoRepo, pagoService);
            WebhookService mercadoPagoWebhookService =
                    new WebhookService(negocioRepo, pagoRepo, ventaRepo, eventBus);
            StripeWebhookService stripeWebhookService =
                    new StripeWebhookService(negocioRepo, pagoRepo, ventaRepo, eventBus);
            AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepo, eventBus);
            StockService stockService = new StockService();
            RecomendacionService recomendacionService = new RecomendacionService();

 
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

   
            server.createContext("/api/productos", new ProductoController(productoService));
            server.createContext("/api/clientes", new ClienteController(clienteService));
            server.createContext("/api/metodos-pago", new MetodoPagoController(metodoPagoService));
            server.createContext("/api/ventas", new VentaController(ventaService, productoRepo));
            server.createContext("/api/webhooks/mercado-pago",
                    new WebhookController(mercadoPagoWebhookService));
            server.createContext("/api/webhooks/stripe",
                    new StripeWebhookController(stripeWebhookService));
            server.createContext("/api/auditoria", new AuditoriaController(auditoriaService));
            server.createContext("/api/stock", new StockController(stockService, productoRepo));
            server.createContext("/api/recomendaciones", new RecomendacionController(recomendacionService, productoRepo));

     
            server.setExecutor(Executors.newCachedThreadPool());


            server.start();
            System.out.println("✅ Servidor HTTP iniciado correctamente en http://localhost:8080/");
            System.out.println("Rutas disponibles:");
            System.out.println("  - Productos: GET/POST/PUT/DELETE /api/productos");
            System.out.println("  - Clientes: GET/POST/PUT/DELETE /api/clientes");
            System.out.println("  - Ventas: POST /api/ventas/previsualizar, POST /api/ventas/crear, GET /api/ventas/{id}, POST /api/ventas/{id}/pago, POST /api/ventas/{id}/cancelar");
            System.out.println("\nPresione Ctrl+C para detener el servidor.");

        } catch (IOException e) {
            System.err.println("❌ Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
