package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.CarritoPersistido;
import com.CapaDominio.Excepciones.StockInsuficienteException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

@ExtendWith(MockitoExtension.class)
class RedisCarritoRepositoryTest {

    private static final String CLAVE = "carrito:negocio:1:cliente:7";

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    @Mock
    private Transaction transaction;

    private RedisCarritoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisCarritoRepository(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("obtener usa una clave aislada por negocio y cliente")
    void obtenerAislaPorNegocioYCliente() {
        when(jedis.get(CLAVE)).thenReturn("{\"2\":3}");

        Optional<CarritoPersistido> resultado = repository.obtener(1L, 7);

        assertTrue(resultado.isPresent());
        assertEquals(3, resultado.get().cantidades().get(2));
        verify(jedis).get(CLAVE);
    }

    @Test
    @DisplayName("eliminar usa la misma clave tenant-scoped")
    void eliminarAislaPorNegocioYCliente() {
        repository.eliminar(1L, 7);

        verify(jedis).del(CLAVE);
    }

    @Test
    @DisplayName("agregarItem fusiona cantidades dentro de una transacción Redis")
    void agregarItemFusionaCantidades() {
        when(jedis.get(CLAVE)).thenReturn("{\"2\":2}");
        when(jedis.multi()).thenReturn(transaction);
        when(transaction.exec()).thenReturn(List.of("OK"));

        CarritoPersistido resultado = repository.agregarItem(1L, 7, 2, 3, 10);

        assertEquals(5, resultado.cantidades().get(2));
        verify(transaction).setex(eq(CLAVE), eq(86400L), anyString());
    }

    @Test
    @DisplayName("agregarItem rechaza superar el stock sin abrir transacción")
    void agregarItemRechazaStock() {
        when(jedis.get(CLAVE)).thenReturn("{\"2\":2}");

        assertThrows(StockInsuficienteException.class,
                () -> repository.agregarItem(1L, 7, 2, 3, 4));

        verify(jedis, never()).multi();
    }
}
