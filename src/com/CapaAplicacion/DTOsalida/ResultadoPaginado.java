package com.CapaAplicacion.DTOsalida;

import java.util.List;

public record ResultadoPaginado<T>(
        List<T> datos,
        int paginaActual,
        int totalPaginas,
        long totalRegistros,
        int tamanioPagina
) {
    public ResultadoPaginado(List<T> datos, int paginaActual, int tamanioPagina, long totalRegistros) {
        this(
                datos == null ? List.of() : datos,
                Math.max(1, paginaActual),
                tamanioPagina > 0 ? (int) Math.ceil((double) Math.max(0, totalRegistros) / tamanioPagina) : 0,
                Math.max(0, totalRegistros),
                Math.max(1, tamanioPagina)
        );
    }
}
