package com.CapaAplicacion.Service;

import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaAplicacion.Interfaces.IVentasService;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaDominio.Entidades.Cuenta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class VentaService implements IVentasService {

    private final IVentaRepository ventaRepository;
    private final IProductoRepository productoRepository;
    private final ICuentaRepositorio cuentaRepository;
    private final EventBus eventBus;

    public VentaService(IVentaRepository ventaRepository, IProductoRepository productoRepository, ICuentaRepositorio cuentaRepository, EventBus eventBus) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.cuentaRepository = cuentaRepository;
        this.eventBus = eventBus;
    }


public CompletableFuture<Void> crearVentaAsync(final Carrito carrito, 
                                                  final String tipoPago, 
                                                  final IDescuentos descuento) {
        return CompletableFuture.runAsync(() -> {
                if(carrito == null || carrito.getItems().isEmpty()) {
                    throw new IllegalArgumentException("El carrito no puede estar vacío para crear una venta.");
                }
//primer pasada de validacion 
                for(ItemCarrito item : carrito.getItems().values()){
                        Producto prod = item.getProducto(); 
                    if(prod.getStockDisponible() < item.getCantidad()){
                        throw new IllegalArgumentException("No hay suficiente stock para el producto: " + prod.getNombre());

                    }
                }
                TipoDePago tipoDePagoEnum = TipoDePago.valueOf(tipoPago.toUpperCase());
                
           
                Venta nuevaVenta = new Venta(carrito.getIdCliente(), tipoDePagoEnum);

               
                nuevaVenta.cargarDetallesDesdeCarrito(carrito, descuento);

                
                for (ItemCarrito item : carrito.getItems().values()) {
                    Producto prod = item.getProducto();
                    prod.reducirStock(item.getCantidad());
                    productoRepository.actualizar(prod.getId(), prod);
                }

                ventaRepository.guardar(nuevaVenta);
                carrito.vaciar(); 

      
                eventBus.publicar(new VentasEventos("CREADA", nuevaVenta));
            });
    }

    @Override
    public CompletableFuture<Optional<Venta>> buscarPorIdAsync(final int id) {
        return CompletableFuture.supplyAsync(new Supplier<Optional<Venta>>() {
            @Override
            public Optional<Venta> get() {
                return ventaRepository.buscarPorId(id);
            }
        });
    }


    @Override
    public CompletableFuture<Void> registrarPagoAsync(final int idVenta) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    
                    
                    Optional<Venta> ventaOpt = ventaRepository.buscarPorId(idVenta);
                    if (!ventaOpt.isPresent()) {
                        throw new RuntimeException("No se puede pagar. Venta no encontrada con ID: " + idVenta);
                    }
                    Venta venta = ventaOpt.get();

                    List<Cuenta> cuentasCliente = cuentaRepository.buscarPorIdCliente(venta.getIdCliente());
                    if (cuentasCliente.isEmpty()) {
                        throw new RuntimeException("El cliente no posee cuentas registradas para realizar el pago.");
                    }

                    BigDecimal totalVenta = venta.calculoTotal();
                    Cuenta cuentaSeleccionada = cuentasCliente.stream()
                            .filter(cuenta -> cuenta.getSaldo().compareTo(totalVenta) >= 0)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException(
                                    "Saldo insuficiente. Ninguna cuenta del cliente cubre el total de la venta."));

                    venta.procesarPagoConCuenta(cuentaSeleccionada);

                    cuentaRepository.actualizar(cuentaSeleccionada.getId(), cuentaSeleccionada);
                    ventaRepository.actualizar(idVenta, venta);

                    eventBus.publicar(new VentasEventos("PAGADA", venta));
                    
                } catch (Exception e) {
                
                    throw new RuntimeException("Error al registrar el pago: " + e.getMessage(), e);
                }
            }
        });
    }

@Override
    public CompletableFuture<Void> cancelarVentaAsync(final int idVenta) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Optional<Venta> ventaOpt = ventaRepository.buscarPorId(idVenta);
                    if (!ventaOpt.isPresent()) {
                        throw new RuntimeException("No se puede cancelar. Venta no encontrada con ID: " + idVenta);
                    }
                    Venta venta = ventaOpt.get();

                    venta.Cancelar();

                    for (DetalleVenta i : venta.getDetalles()) {
                        Optional<Producto> productoOpt = productoRepository.buscarPorId(i.getIdProducto());
                        if (!productoOpt.isPresent()) {
                            throw new RuntimeException("Error crítico: El producto de la venta ya no existe.");
                        }

                        Producto producto = productoOpt.get();
                      producto.reintegrarStock(i.getCantidad());
                        productoRepository.actualizar(producto.getId(), producto);
                    }


                    ventaRepository.actualizar(idVenta, venta);

               
                    eventBus.publicar(new VentasEventos("CANCELADA", venta));

                } catch (Exception e) {
                    throw new RuntimeException("Error al cancelar la venta de forma atómica: " + e.getMessage(), e);
                }
            }
        });
    }
}