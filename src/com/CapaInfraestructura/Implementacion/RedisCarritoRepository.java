package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaDominio.Entidades.Carrito;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

public class RedisCarritoRepository implements ICarritoRepository {
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private static final int TTL_24_HORAS = 86400; // Expiración en segundos

    public RedisCarritoRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
    }

    private String generarClave(int idCliente) {
        return "carrito:cliente:" + idCliente;
    }

    @Override
    public Optional<Carrito> obtenerPorClienteId(int idCliente) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(generarClave(idCliente));
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            Carrito carrito = objectMapper.readValue(json, Carrito.class);
            return Optional.of(carrito);
        } catch (Exception e) {
            throw new RuntimeException("Error I/O al leer el carrito desde Redis", e);
        }
    }

    @Override
    public void guardar(Carrito carrito) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(carrito);
            String key = generarClave(carrito.getIdCliente());
            
       
            jedis.setex(key, TTL_24_HORAS, json);
        } catch (Exception e) {
            throw new RuntimeException("Error I/O al guardar el carrito en Redis", e);
        }
    }

    @Override
    public void eliminarPorClienteId(int idCliente) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(generarClave(idCliente));
        } catch (Exception e) {
            throw new RuntimeException("Error I/O al eliminar el carrito de Redis", e);
        }
    }
} 