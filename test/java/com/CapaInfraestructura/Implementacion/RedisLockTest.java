package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLockTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private RedisLock redisLock;

    @BeforeEach
    void setUp() {
        redisLock = new RedisLock(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("adquiere lock exitosamente cuando SET retorna OK")
    void adquiereLockExitosamente() {
        when(jedis.set(eq("job:test"), eq("LOCK"), any(SetParams.class))).thenReturn("OK");

        boolean adquirido = redisLock.intentarAdquirir("job:test", 60);

        assertTrue(adquirido);
    }

    @Test
    @DisplayName("retorna falso cuando el lock ya existe y SET retorna null")
    void fallaAdquirirLockExistente() {
        when(jedis.set(eq("job:test"), eq("LOCK"), any(SetParams.class))).thenReturn(null);

        boolean adquirido = redisLock.intentarAdquirir("job:test", 60);

        assertFalse(adquirido);
    }

    @Test
    @DisplayName("aplica Fail-Open retornando true si Redis falla al adquirir")
    void aplicaFailOpenAlAdquirir() {
        when(jedis.set(eq("job:test"), eq("LOCK"), any(SetParams.class))).thenThrow(new RuntimeException("Redis down"));

        boolean adquirido = redisLock.intentarAdquirir("job:test", 60);

        assertTrue(adquirido);
    }

    @Test
    @DisplayName("libera lock correctamente usando DEL")
    void liberaLockCorrectamente() {
        redisLock.liberar("job:test");

        verify(jedis).del("job:test");
    }

    @Test
    @DisplayName("lanza excepción si el jedisPool es nulo")
    void validaPoolNulo() {
        assertThrows(IllegalArgumentException.class, () -> new RedisLock(null));
    }
}
