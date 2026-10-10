

package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Venta;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface IVentaRepository {
    void guardar(Venta venta);
    default void guardar(Venta venta, Connection connection) throws java.sql.SQLException {
        guardar(venta);
    }
    Optional<Venta> buscarPorId(int id);
    default Optional<Venta> buscarPorClaveIdempotenciaSolicitud(String claveIdempotenciaSolicitud) {
        return Optional.empty();
    }
    default boolean marcarComoPagada(int id) {
        Optional<Venta> venta = buscarPorId(id);
        if (venta.isEmpty() || venta.get().getEstadoCompra()
                != com.CapaDominio.Entidades.EstadoVenta.PENDIENTE) {
            return false;
        }
        venta.get().Pagar();
        actualizar(id, venta.get());
        return true;
    }
    List<Venta> listarTodas();
    void actualizar(int id, Venta venta);
    default void actualizar(int id, Venta venta, Connection connection) throws java.sql.SQLException {
        actualizar(id, venta);
    }
}
