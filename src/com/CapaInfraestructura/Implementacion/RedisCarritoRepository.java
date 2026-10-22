package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.CarritoPersistido;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaDominio.Excepciones.StockInsuficienteException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class RedisCarritoRepository implements ICarritoRepository, AutoCloseable {
    private static final int TTL_24_HORAS = 86400;
    private static final int MAX_INTENTOS_CONCURRENCIA = 5;
    private static final TypeReference<Map<Integer, Integer>> TIPO_CANTIDADES =
            new TypeReference<>() {
            };

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisCarritoRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
    }

    private String generarClave(long idNegocio, int idCliente) {
        return "carrito:negocio:" + idNegocio + ":cliente:" + idCliente;
    }

    @Override
    public Optional<CarritoPersistido> obtener(long idNegocio, int idCliente) {
        validarClaves(idNegocio, idCliente);
        try (Jedis jedis = jedisPool.getResource()) {
            Map<Integer, Integer> cantidades = leer(jedis.get(generarClave(idNegocio, idCliente)));
            if (cantidades.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CarritoPersistido(idCliente, cantidades));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("El carrito almacenado en Redis no es válido", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error I/O al leer el carrito desde Redis", e);
        }
    }

    @Override
    public CarritoPersistido agregarItem(long idNegocio, int idCliente, int idProducto,
                                         int cantidad, int maxCantidad) {
        validarClaves(idNegocio, idCliente);
        if (idProducto <= 0 || cantidad <= 0) {
            throw new IllegalArgumentException("El producto y la cantidad deben ser positivos.");
        }
        return mutar(idNegocio, idCliente, new MutadorCantidades() {
            @Override
            public Map<Integer, Integer> aplicar(Map<Integer, Integer> actual) {
                int cantidadActual = actual.getOrDefault(idProducto, 0);
                long cantidadNueva = (long) cantidadActual + cantidad;
                if (maxCantidad <= 0 || cantidadNueva > maxCantidad) {
                    throw new StockInsuficienteException(
                            "No hay stock suficiente para agregar ese producto al carrito.");
                }
                actual.put(idProducto, (int) cantidadNueva);
                return actual;
            }
        });
    }

    @Override
    public CarritoPersistido eliminarItem(long idNegocio, int idCliente, int idProducto) {
        validarClaves(idNegocio, idCliente);
        if (idProducto <= 0) {
            throw new IllegalArgumentException("El producto debe ser positivo.");
        }
        return mutar(idNegocio, idCliente, new MutadorCantidades() {
            @Override
            public Map<Integer, Integer> aplicar(Map<Integer, Integer> actual) {
                actual.remove(idProducto);
                return actual;
            }
        });
    }

    @Override
    public void eliminar(long idNegocio, int idCliente) {
        validarClaves(idNegocio, idCliente);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(generarClave(idNegocio, idCliente));
        } catch (Exception e) {
            throw new RuntimeException("Error I/O al eliminar el carrito de Redis", e);
        }
    }

    private CarritoPersistido mutar(long idNegocio, int idCliente, MutadorCantidades mutador) {
        String clave = generarClave(idNegocio, idCliente);
        for (int intento = 0; intento < MAX_INTENTOS_CONCURRENCIA; intento++) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.watch(clave);
                Map<Integer, Integer> cantidades = leer(jedis.get(clave));
                Map<Integer, Integer> actualizadas = mutador.aplicar(cantidades);
                String json = objectMapper.writeValueAsString(actualizadas);
                boolean aplicado = false;
                Transaction transaccion = jedis.multi();
                try {
                    if (actualizadas.isEmpty()) {
                        transaccion.del(clave);
                    } else {
                        transaccion.setex(clave, TTL_24_HORAS, json);
                    }
                    aplicado = transaccion.exec() != null;
                } finally {
                    transaccion.close();
                }
                if (aplicado) {
                    return new CarritoPersistido(idCliente, actualizadas);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("No se pudo serializar el carrito para Redis", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Error I/O al actualizar el carrito en Redis", e);
            }
        }
        throw new IllegalStateException(
                "No se pudo actualizar el carrito por concurrencia; reintente la operación.");
    }

    private Map<Integer, Integer> leer(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<Integer, Integer> cantidades = objectMapper.readValue(json, TIPO_CANTIDADES);
        return cantidades == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cantidades);
    }

    private void validarClaves(long idNegocio, int idCliente) {
        if (idNegocio <= 0 || idCliente <= 0) {
            throw new IllegalArgumentException("El negocio y el cliente deben ser positivos.");
        }
    }

    @Override
    public void close() {
        jedisPool.close();
    }

    private interface MutadorCantidades {
        Map<Integer, Integer> aplicar(Map<Integer, Integer> actual);
    }
}
