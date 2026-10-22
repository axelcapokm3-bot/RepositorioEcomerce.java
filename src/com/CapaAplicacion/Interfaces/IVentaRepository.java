

package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Venta;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface IVentaRepository {
    void guardar(Venta venta);
    default void guardar(Venta venta, Connection connection) throws java.sql.SQLException {
        guardar(venta);
    }
    Optional<Venta> buscarPorId(long idNegocio, int id);
    default Optional<Venta> buscarPorIdParaUpdate(long idNegocio, int id, Connection connection)
            throws java.sql.SQLException {
        return buscarPorId(idNegocio, id);
    }
    default Optional<Venta> buscarPorClaveIdempotenciaSolicitud(long idNegocio, String claveIdempotenciaSolicitud) {
        return Optional.empty();
    }
    default List<Venta> buscarPendientesAnteriores(LocalDateTime limite, int cantidad) {
        return Collections.emptyList();
    }
    default boolean marcarComoPagada(long idNegocio, int id) {
        Optional<Venta> venta = buscarPorId(idNegocio, id);
        if (venta.isEmpty() || venta.get().getEstadoCompra()
                != com.CapaDominio.Entidades.EstadoVenta.PENDIENTE) {
            return false;
        }
        venta.get().Pagar();
        actualizar(idNegocio, id, venta.get());
        return true;
    }
    List<Venta> listarTodas(long idNegocio);
    void actualizar(long idNegocio, int id, Venta venta);
    default void actualizar(long idNegocio, int id, Venta venta, Connection connection) throws java.sql.SQLException {
        actualizar(idNegocio, id, venta);
    }
}
