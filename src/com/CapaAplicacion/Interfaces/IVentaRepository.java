

package com.CapaAplicacion.Interfaces;

import com.CapaDominio.Entidades.Venta;
import java.util.List;
import java.util.Optional;

public interface IVentaRepository {
    void guardar(Venta venta);
    Optional<Venta> buscarPorId(int id);
    List<Venta> listarTodas();
    void actualizar(int id, Venta venta);
}
