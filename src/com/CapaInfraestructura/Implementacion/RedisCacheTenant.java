package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Excepciones.CacheNoDisponibleException;
import com.CapaAplicacion.Interfaces.RepositorioCacheTenant;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisCacheTenant implements RepositorioCacheTenant {

    private static final String PREFIJO_CLAVE = "negocio:subdominio:";

    private final JedisPool jedisPool;

    public RedisCacheTenant(JedisPool jedisPool) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("El pool de Redis es obligatorio.");
        }
        this.jedisPool = jedisPool;
    }

    @Override
    public void guardar(String subdominio, String idNegocio, long ttlSegundos) {
        if (idNegocio == null || idNegocio.isBlank()) {
            throw new IllegalArgumentException("El id del negocio es obligatorio.");
        }
        if (ttlSegundos <= 0) {
            throw new IllegalArgumentException("El TTL debe ser positivo.");
        }
        String clave = generarClave(subdominio);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(clave, ttlSegundos, idNegocio);
        } catch (RuntimeException e) {
            throw new CacheNoDisponibleException(
                    "No se pudo guardar el tenant en la cache de Redis.", e);
        }
    }

    @Override
    public String obtener(String subdominio) {
        String clave = generarClave(subdominio);
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(clave);
        } catch (RuntimeException e) {
            throw new CacheNoDisponibleException(
                    "No se pudo leer el tenant desde la cache de Redis.", e);
        }
    }

    @Override
    public void invalidar(String subdominio) {
        String clave = generarClave(subdominio);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(clave);
        } catch (RuntimeException e) {
            throw new CacheNoDisponibleException(
                    "No se pudo invalidar el tenant en la cache de Redis.", e);
        }
    }

    private String generarClave(String subdominio) {
        if (subdominio == null || subdominio.isBlank()) {
            throw new IllegalArgumentException("El subdominio es obligatorio.");
        }
        return PREFIJO_CLAVE + subdominio;
    }
}
