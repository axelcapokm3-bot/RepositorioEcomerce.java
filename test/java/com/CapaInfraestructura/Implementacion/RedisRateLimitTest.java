package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimitTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private RedisRateLimit rateLimit;

    @BeforeEach
    void setUp() {
        rateLimit = new RedisRateLimit(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("ejecuta script Lua y retorna contador actual")
    void incrementaYVerificaConLua() {
        when(jedis.eval(any(String.class), eq(List.of("rate-limit:ip|1|test")), eq(List.of("60"))))
                .thenReturn(1L);

        long count = rateLimit.incrementarYVerificar("ip|1|test", 5, 60);

        assertEquals(1L, count);
    }

    @Test
    @DisplayName("aplica Fail-Open retornando 0 si Redis falla")
    void aplicaFailOpenSiRedisFalla() {
        when(jedis.eval(any(String.class), any(), any())).thenThrow(new RuntimeException("Redis down"));

        long count = rateLimit.incrementarYVerificar("ip|1|test", 5, 60);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("lanza excepción si el jedisPool es nulo")
    void validaPoolNulo() {
        assertThrows(IllegalArgumentException.class, () -> new RedisRateLimit(null));
    }
}
