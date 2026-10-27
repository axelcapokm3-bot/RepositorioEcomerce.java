package com.CapaExterna;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INotificadorEmail;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaInfraestructura.Implementacion.JDBCTransaccion;
import com.CapaInfraestructura.Implementacion.NotificadorEmailMock;
import com.CapaInfraestructura.Implementacion.NotificacionListener;
import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaNegocioRepositorio;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Interfaces.IEventoWebhookRepository;
import com.CapaAplicacion.Interfaces.IInvalidadorCacheNegocio;
import com.CapaAplicacion.Service.AuditoriaService;
import com.CapaAplicacion.Service.AutenticacionService;
import com.CapaAplicacion.Service.CarritoService;
import com.CapaAplicacion.Service.ClienteService;
import com.CapaAplicacion.Service.NegocioService;
import com.CapaAplicacion.Service.ProductoService;
import com.CapaAplicacion.Service.PagoService;
import com.CapaAplicacion.Service.ProcesadorEventosWebhook;
import com.CapaAplicacion.Service.RecomendacionService;
import com.CapaAplicacion.Service.StockService;
import com.CapaAplicacion.Service.StripeWebhookService;
import com.CapaAplicacion.Service.ValidadorNegocioService;
import com.CapaAplicacion.Service.VentaService;
import com.CapaAplicacion.Service.WebhookEventoService;
import com.CapaAplicacion.Service.WebhookService;
import com.CapaAplicacion.puerto.ConfiguracionCobro;
import com.CapaAplicacion.puerto.PasarelaPago;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaInfraestructura.Implementacion.AdaptadorContrasena;
import com.CapaInfraestructura.Implementacion.InMemoryEventBus;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCliente;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuenta;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioCuentaNegocio;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioProducto;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioVenta;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioPago;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioEventoWebhook;
import com.CapaInfraestructura.Implementacion.JobWebhooks;
import com.CapaInfraestructura.Implementacion.JobExpiracionVentas;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioAuditoria;
import com.CapaInfraestructura.Implementacion.MySqlRepositorioTokenBootstrap;
import com.CapaInfraestructura.Implementacion.MySqlNegocioRepository;
import com.CapaInfraestructura.Implementacion.PasarelaMercadoPago;
import com.CapaInfraestructura.Implementacion.PasarelaStripe;
import com.CapaInfraestructura.Implementacion.ProveedorTokenAcceso;
import com.CapaInfraestructura.Implementacion.RedisCacheTenant;
import com.CapaInfraestructura.Implementacion.RedisCarritoRepository;
import com.CapaInfraestructura.Implementacion.RedisRateLimit;
import com.CapaInfraestructura.Implementacion.RedisLock;
import com.CapaInfraestructura.Configuracion.ConfiguracionRedis;
import com.CapaInfraestructura.Configuracion.DiagnosticoArranque;
import com.CapaInfraestructura.Configuracion.ResolutorConfiguracion;
import com.CapaPresentacion.Controllers.AuditoriaController;
import com.CapaPresentacion.Controllers.AuthController;
import com.CapaPresentacion.Controllers.CarritoController;
import com.CapaPresentacion.Controllers.ClienteController;
import com.CapaPresentacion.Controllers.NegocioConfigController;
import com.CapaPresentacion.Controllers.ProductoController;
import com.CapaPresentacion.Controllers.RecomendacionController;
import com.CapaPresentacion.Controllers.StockController;
import com.CapaPresentacion.Controllers.VentaController;
import com.CapaPresentacion.Controllers.WebhookController;
import com.CapaPresentacion.Controllers.StripeWebhookController;
import com.CapaPresentacion.Utilidades.LimitadorTasa;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.JedisPool;

public class ServerMain {

    public static void main(String[] args) {
        try {
            ResolutorConfiguracion.getInstancia().validarArranque();
            RegistroExecutors.validarConfiguracion();
            LimitadorTasa.iniciar();

            System.out.println("==================================================");
            System.out.println("🚀 INICIANDO SERVIDOR HTTP E-COMMERCE EN PUERTO 8080");
            System.out.println("==================================================\n");

         
            IProductoRepository productoRepo = new MySqlRepositorioProducto();
            IVentaRepository ventaRepo = new MySqlRepositorioVenta();
            IPagoRepository pagoRepo = new MySqlRepositorioPago();
            INegocioRepository negocioRepo = new MySqlNegocioRepository();
            IClienteRepositorio clienteRepo = new MySqlRepositorioCliente();
            IAuditoriaRepository auditoriaRepo = new MySqlRepositorioAuditoria();
            ICuentaRepositorio cuentaRepo = new MySqlRepositorioCuenta();
            ICuentaNegocioRepositorio vinculoRepo = new MySqlRepositorioCuentaNegocio();
            JedisPool jedisPool = ConfiguracionRedis.desdeEntorno().crearPool();
            RedisCarritoRepository carritoRepo = new RedisCarritoRepository(jedisPool);
            RedisCacheTenant cacheTenant = new RedisCacheTenant(jedisPool);
            RedisRateLimit redisRateLimit = new RedisRateLimit(jedisPool);
            RedisLock redisLock = new RedisLock(jedisPool);
            LimitadorTasa.setRepositorioRateLimit(redisRateLimit);
            ResolutorNegocio resolutorNegocio = new ResolutorNegocio(negocioRepo, cacheTenant);
            IEventoWebhookRepository eventoWebhookRepo = new MySqlRepositorioEventoWebhook();

            ITransaccion transaccion = new JDBCTransaccion();
            EventBus eventBus = new InMemoryEventBus();
            INotificadorEmail notificador = new NotificadorEmailMock();
            new NotificacionListener(eventBus, notificador);

            IInvalidadorCacheNegocio invalidadorCache = new IInvalidadorCacheNegocio() {
                @Override
                public void invalidar(long idNegocio) {
                    resolutorNegocio.invalidarPorId(String.valueOf(idNegocio));
                }
            };
            ValidadorNegocioService validadorNegocio = new ValidadorNegocioService(negocioRepo);
            ConfiguracionCobro configuracionCobro =
                    new com.CapaInfraestructura.Implementacion.ConfiguracionCobro(negocioRepo);
            Map<ProveedorPago, PasarelaPago> pasarelas = Map.of(
                    ProveedorPago.MERCADO_PAGO, new PasarelaMercadoPago(negocioRepo),
                    ProveedorPago.STRIPE, new PasarelaStripe(negocioRepo));
            PagoService pagoService = new PagoService(
                    pagoRepo, ventaRepo, eventBus, transaccion, pasarelas, configuracionCobro);
            NegocioService negocioService = new NegocioService(negocioRepo, invalidadorCache);

          
            ProductoService productoService = new ProductoService(productoRepo, eventBus);
            CarritoService carritoService = new CarritoService(carritoRepo, productoRepo, validadorNegocio);
            ClienteService clienteService = new ClienteService(clienteRepo);
            VentaService ventaService = new VentaService(transaccion, ventaRepo, productoRepo, clienteRepo,
                    eventBus, carritoRepo, pagoService, validadorNegocio);
            WebhookService mercadoPagoWebhookService =
                    new WebhookService(negocioRepo, pagoRepo, ventaRepo, eventBus, pagoService);
            StripeWebhookService stripeWebhookService =
                    new StripeWebhookService(negocioRepo, pagoRepo, ventaRepo, eventBus, pagoService);
            ProcesadorEventosWebhook procesadorEventos =
                    new ProcesadorEventosWebhook(mercadoPagoWebhookService, stripeWebhookService);
            WebhookEventoService eventoWebhookService = new WebhookEventoService(
                    eventoWebhookRepo,
                    procesadorEventos,
                    RegistroExecutors.obtener(
                            com.CapaAplicacion.Interfaces.TipoServicio.NOTIFICACIONES),
                    enteroConfiguracion("webhook.outbox.max-intentos", 10),
                    enteroConfiguracion("webhook.outbox.lote", 50));
            JobWebhooks jobWebhooks = new JobWebhooks(
                    eventoWebhookService,
                    enteroConfiguracion("webhook.outbox.intervalo-segundos", 30),
                    enteroConfiguracion("webhook.outbox.recuperacion-minutos", 5));
            JobExpiracionVentas jobExpiracionVentas = new JobExpiracionVentas(
                    ventaService,
                    redisLock,
                    enteroConfiguracion("ventas.expiracion.intervalo-segundos", 60),
                    enteroConfiguracion("ventas.expiracion.minutos", 30),
                    enteroConfiguracion("ventas.expiracion.lote", 50));

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    jobWebhooks.close();
                    jobExpiracionVentas.close();
                    LimitadorTasa.detener();
                    carritoRepo.close();
                    RegistroExecutors.cerrarTodos();
                }
            }, "executor-shutdown-hook"));

            AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepo, eventBus);
            ProveedorTokenAcceso proveedorToken = new ProveedorTokenAcceso();
            MySqlRepositorioTokenBootstrap tokenBootstrapRepo = new MySqlRepositorioTokenBootstrap();
            AutenticacionService autenticacionService = new AutenticacionService(
                    cuentaRepo, clienteRepo, vinculoRepo, negocioRepo, new AdaptadorContrasena(), proveedorToken,
                    transaccion,
                    RegistroExecutors.obtener(
                            com.CapaAplicacion.Interfaces.TipoServicio.AUTENTICACION),
                    com.CapaInfraestructura.Configuracion.ConfigLoader.getSecretProperty("auth.bootstrap-token"),
                    invalidadorCache,
                    tokenBootstrapRepo);
            autenticacionService.prepararTokenBootstrap();
            StockService stockService = new StockService();
            RecomendacionService recomendacionService = new RecomendacionService();

 
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

   
            server.createContext("/api/productos",
                    new ProductoController(productoService, proveedorToken, resolutorNegocio));
            server.createContext("/api/clientes",
                    new ClienteController(clienteService, proveedorToken, resolutorNegocio));
            server.createContext("/api/auth",
                    new AuthController(autenticacionService, proveedorToken, resolutorNegocio));
            server.createContext("/api/ventas", new VentaController(
                    ventaService, productoRepo, ventaRepo, proveedorToken, validadorNegocio,
                    resolutorNegocio));
            server.createContext("/api/carrito",
                    new CarritoController(carritoService, proveedorToken, resolutorNegocio));
            server.createContext("/api/webhooks/mercado-pago",
                    new WebhookController(mercadoPagoWebhookService, eventoWebhookService,
                            resolutorNegocio));
            server.createContext("/api/webhooks/stripe",
                    new StripeWebhookController(stripeWebhookService, eventoWebhookService,
                            resolutorNegocio));
            server.createContext("/api/negocios",
                    new NegocioConfigController(negocioService, proveedorToken, resolutorNegocio));
            server.createContext("/api/auditoria",
                    new AuditoriaController(auditoriaService, proveedorToken, resolutorNegocio));
            server.createContext("/api/stock",
                    new StockController(stockService, productoRepo, proveedorToken, resolutorNegocio));
            server.createContext("/api/recomendaciones",
                    new RecomendacionController(recomendacionService, productoRepo, proveedorToken,
                            resolutorNegocio));

     
            server.setExecutor(crearExecutorHttp());


            server.start();
            jobWebhooks.iniciar();
            jobExpiracionVentas.iniciar();
            System.out.println("✅ Servidor HTTP iniciado correctamente en http://localhost:8080/");
            System.out.println("Rutas disponibles:");
            System.out.println("  - Productos: GET/POST/PUT/DELETE /api/productos");
            System.out.println("  - Clientes: GET/POST/PUT/DELETE /api/clientes");
            System.out.println("  - Ventas: POST /api/ventas/previsualizar, POST /api/ventas/crear, GET /api/ventas/{id}, POST /api/ventas/{id}/pago, POST /api/ventas/{id}/cancelar");
            System.out.println("  - Carrito: GET/DELETE /api/carrito, POST /api/carrito/items, DELETE /api/carrito/items/{idProducto}");
            System.out.println("\nPresione Ctrl+C para detener el servidor.");

        } catch (IOException e) {
            DiagnosticoArranque.registrar("startup.fail", "ServerMain", e);
            System.exit(DiagnosticoArranque.CODIGO_SALIDA_FALLO);
        } catch (ExceptionInInitializerError e) {
            DiagnosticoArranque.registrar("startup.fail", "ServerMain",
                    e.getCause() == null ? e : e.getCause());
            System.exit(DiagnosticoArranque.CODIGO_SALIDA_FALLO);
        } catch (RuntimeException e) {
            DiagnosticoArranque.registrar("startup.fail", "ServerMain", e);
            System.exit(DiagnosticoArranque.CODIGO_SALIDA_FALLO);
        }
    }

    private static int enteroConfiguracion(String clave, int defecto) {
        String valor = com.CapaInfraestructura.Configuracion.ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return defecto;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return numero > 0 ? numero : defecto;
        } catch (NumberFormatException exception) {
            return defecto;
        }
    }

    private static java.util.concurrent.Executor crearExecutorHttp() {
        java.util.concurrent.ThreadFactory fabrica = new java.util.concurrent.ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger contador =
                    new java.util.concurrent.atomic.AtomicInteger(1);

            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "http-" + contador.getAndIncrement());
                hilo.setDaemon(false);
                return hilo;
            }
        };
        return new java.util.concurrent.ThreadPoolExecutor(
                50,
                200,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(2000),
                fabrica,
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    }

}
