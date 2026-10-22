package com.CapaAplicacion.Interfaces;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CarritoPersistido(int idCliente, Map<Integer, Integer> cantidades) {

    public CarritoPersistido {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("El ID del cliente debe ser positivo.");
        }
        Map<Integer, Integer> copia = new LinkedHashMap<>();
        if (cantidades != null) {
            for (Map.Entry<Integer, Integer> entrada : cantidades.entrySet()) {
                if (entrada.getKey() == null || entrada.getKey() <= 0) {
                    throw new IllegalArgumentException("El carrito contiene un producto inválido.");
                }
                if (entrada.getValue() == null || entrada.getValue() <= 0) {
                    throw new IllegalArgumentException("El carrito contiene una cantidad inválida.");
                }
                copia.put(entrada.getKey(), entrada.getValue());
            }
        }
        cantidades = Collections.unmodifiableMap(copia);
    }

    public static CarritoPersistido vacio(int idCliente) {
        return new CarritoPersistido(idCliente, Collections.emptyMap());
    }
}
