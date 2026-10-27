package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.AdquiridorLock;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisLock implements AdquiridorLock {
    private static final Logger LOGGER = Logger.getLogger(RedisLock.class.getName());
    private final JedisPool jedisPool;

    public RedisLock(JedisPool jedisPool) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("El pool de Redis es obligatorio.");
        }
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean intentarAdquirir(String clave, long ttlSegundos) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("La clave del lock es obligatoria.");
        }
        if (ttlSegundos <= 0) {
            throw new IllegalArgumentException("El TTL en segundos debe ser positivo.");
        }
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = new SetParams().nx().px(ttlSegundos * 1000);
            String respuesta = jedis.set(clave, "LOCK", params);
            return "OK".equals(respuesta);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error crítico en Redis al intentar adquirir lock '" + clave + "' (Fail-Open habilitado): " + e.getMessage(), e);
            return true;
        }
    }

    @Override
    public void liberar(String clave) {
        if (clave == null || clave.isBlank()) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(clave);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No se pudo liberar el lock '" + clave + "' en Redis: " + e.getMessage(), e);
        }
    }
}
