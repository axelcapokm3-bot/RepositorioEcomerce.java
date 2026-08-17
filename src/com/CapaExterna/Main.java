
package com.CapaExterna;

import com.CapaAplicacion.Interfaces.*;
import com.CapaAplicacion.Service.*;
import com.CapaDominio.Entidades.*;
import com.CapaInfraestructura.Implementacion.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("🚀 INICIANDO PRUEBA INTEGRAL DEL SISTEMA E-COMMERCE");
        System.out.println("==================================================\n");

        try {

            IProductoRepository productoRepo = new RepositorioProducto();
            IVentaRepository ventaRepo = new RepositorioVentas();
            ICuentaRepositorio cuentaRepo = new RepositorioCuenta();

            EventBus eventBus = new EventBus() {
                @Override
                public void publicar(Object evento) {
                    System.out.println("📢 [EVENT BUS]: Evento publicado -> " + evento.getClass().getSimpleName());
                }

                @Override
                public <T> void suscribirse(Class<T> tipoEvento, java.util.function.Consumer<T> suscriptor) {
                    System.out.println("🔔 [EVENT BUS]: Suscriptor registrado para -> " + tipoEvento.getSimpleName());
                }
            };

  
            VentaService ventaService = new VentaService(ventaRepo, productoRepo, cuentaRepo, eventBus);
            RecomendacionService recService = new RecomendacionService();
            StockService stockService = new StockService();

       Producto laptop =new Producto("Laptop Gamer", BigDecimal.valueOf(1000.0), 10, 0, 1, CategoriaProducto.ELECTRONICA);
            Producto mouse = new Producto("Mouse Vertical", BigDecimal.valueOf(50.0), 15, 0, 2, CategoriaProducto.ELECTRONICA);
            Producto teclado = new Producto("Teclado Mecanico", BigDecimal.valueOf(120.0), 8, 0, 3, CategoriaProducto.ELECTRONICA);

            productoRepo.guardar(laptop);
            productoRepo.guardar(mouse);
            productoRepo.guardar(teclado);

            System.out.println("📦 Catálogo cargado con éxito. Total productos: " + productoRepo.listarTodos().size());

     
            System.out.println("\n--- 🔍 EVALUACIÓN DE STOCK Y RECOMENDACIONES ---");
            List<Producto> catalogo = productoRepo.listarTodos();

            for (Producto p : catalogo) {
                EstadoStock estado = stockService.evaluarStock(p);
                System.out.println("Producto: " + p.getNombre() + " | Precio: $" + p.getPrecio() + " | Stock Estado: " + estado);
            }

            List<Producto> recomendados = recService.obtenerRecomendados(laptop, catalogo, BigDecimal.valueOf(40.0), BigDecimal.valueOf(200.0));
            System.out.println("Productos recomendados para '" + laptop.getNombre() + "': " + recomendados.size());

 
            System.out.println("\n--- 🛒 PROCESANDO VENTA Y DESCUENTOS ---");
            Carrito carrito = new Carrito(101);

            carrito.agregarItem(new ItemCarrito(laptop, 2));
            carrito.agregarItem(new ItemCarrito(mouse, 1));

          
          IDescuentos estrategiaDescuento = new DescuentoPorPorcentaje(BigDecimal.valueOf(0.10));

            BigDecimal totalBruto = carrito.calcularTotalCarrito(null);
BigDecimal totalConDescuento = carrito.calcularTotalCarrito(estrategiaDescuento);

            System.out.println("Subtotal Bruto: $" + totalBruto);
            System.out.println("Aplicando estrategia: " + estrategiaDescuento.getNombre());
            System.out.println("Total Final con Descuento: $" + totalConDescuento);

            System.out.println("\n⏳ Ejecutando crearVentaAsync...");
            CompletableFuture<Void> futuroVenta = ventaService.crearVentaAsync(carrito, "EFECTIVO", estrategiaDescuento);
            
 
            futuroVenta.join();


            System.out.println("\n--- ✅ VERIFICACIÓN DE ESTADOS POST-VENTA ---");
            System.out.println("1. Ítems restantes en el carrito: " + carrito.getItems().size() + " (Esperado: 0)");

            Producto laptopActualizada = productoRepo.buscarPorId(1).get();
            System.out.println("2. Stock de Laptop tras la compra: " + laptopActualizada.getStockFisico() + " (Esperado: 8)");

            System.out.println("3. Total de ventas registradas: " + ventaRepo.listarTodas().size() + " (Esperado: 1)");

            if (!ventaRepo.listarTodas().isEmpty()) {
                Venta ventaRegistrada = ventaRepo.listarTodas().get(0);
                System.out.println("   └─ Venta ID: " + ventaRegistrada.getId() +
                                   " | Estado: " + ventaRegistrada.getEstadoCompra() +
                                   " | Total Final: $" + ventaRegistrada.calculoTotal());
            }

            System.out.println("\n==================================================");
            System.out.println("🎉 ¡PRUEBA DEL SISTEMA COMPLETADA CON ÉXITO!");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("\n❌ ERROR DURANTE LA EJECUCIÓN: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
