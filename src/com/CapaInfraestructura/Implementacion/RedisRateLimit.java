package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.RepositorioRateLimit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class RedisRateLimit implements RepositorioRateLimit {
    private static final String LUA_SCRIPT =
            "local current = redis.call('INCR', KEYS[1])\n"
                    + "if current == 1 then\n"
                    + "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
                    + "end\n"
                    + "return current;";

    private final JedisPool jedisPool;

    public RedisRateLimit(JedisPool jedisPool) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("El pool de Redis es obligatorio.");
        }
        this.jedisPool = jedisPool;
    }

    @Override
    public long incrementarYVerificar(String clave, long limite, long ventanaSegundos) {
        if (clave == null || clave.isBlank()) {
            return 0L;
        }
        String redisKey = clave.startsWith("rate-limit:") ? clave : "rate-limit:" + clave;
        long ventana = Math.max(1L, ventanaSegundos);
        try (Jedis jedis = jedisPool.getResource()) {
            Object resultado = jedis.eval(LUA_SCRIPT, List.of(redisKey), List.of(String.valueOf(ventana)));
            if (resultado instanceof Number) {
                return ((Number) resultado).longValue();
            }
            if (resultado != null) {
                return Long.parseLong(resultado.toString());
            }
            return 1L;
        } catch (RuntimeException e) {
            System.err.println("Advertencia: Redis no disponible para rate limit (Fail-Open): " + e.getMessage());
            return 0L;
        }
    }
}
