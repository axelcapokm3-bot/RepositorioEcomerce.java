package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Excepciones.CacheNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@ExtendWith(MockitoExtension.class)
class RedisCacheTenantTest {

    private static final String SUBDOMINIO = "tienda";
    private static final String CLAVE = "negocio:subdominio:tienda";

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private RedisCacheTenant cache;

    @BeforeEach
    void setUp() {
        cache = new RedisCacheTenant(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("guarda el ID con SETEX y la clave tenant-scoped")
    void guardaConSetex() {
        cache.guardar(SUBDOMINIO, "10", 60L);

        verify(jedis).setex(CLAVE, 60L, "10");
    }

    @Test
    @DisplayName("obtiene el ID con la clave tenant-scoped")
    void obtieneConClaveTenantScoped() {
        when(jedis.get(CLAVE)).thenReturn("10");

        assertEquals("10", cache.obtener(SUBDOMINIO));
        verify(jedis).get(CLAVE);
    }

    @Test
    @DisplayName("invalida exactamente la clave del subdominio")
    void invalidaClaveExacta() {
        cache.invalidar(SUBDOMINIO);

        verify(jedis).del(CLAVE);
    }

    @Test
    @DisplayName("envuelve los fallos de Redis en CacheNoDisponibleException")
    void envuelveFallosDeRedis() {
        when(jedis.get(CLAVE)).thenThrow(new RuntimeException("down"));

        assertThrows(CacheNoDisponibleException.class, () -> cache.obtener(SUBDOMINIO));
    }
}
