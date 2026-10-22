package com.CapaInfraestructura.Configuracion;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public final class ConfiguracionRedis {

    private final String host;
    private final int puerto;
    private final String usuario;
    private final String contrasena;
    private final boolean ssl;
    private final int baseDatos;
    private final int tiempoConexionMs;
    private final int tiempoSocketMs;
    private final int maximoConexiones;
    private final int maximoInactivas;
    private final int minimoInactivas;

    private ConfiguracionRedis(String host, int puerto, String usuario, String contrasena,
                               boolean ssl, int baseDatos, int tiempoConexionMs, int tiempoSocketMs,
                               int maximoConexiones, int maximoInactivas, int minimoInactivas) {
        this.host = host;
        this.puerto = puerto;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.ssl = ssl;
        this.baseDatos = baseDatos;
        this.tiempoConexionMs = tiempoConexionMs;
        this.tiempoSocketMs = tiempoSocketMs;
        this.maximoConexiones = maximoConexiones;
        this.maximoInactivas = maximoInactivas;
        this.minimoInactivas = minimoInactivas;
    }

    public static ConfiguracionRedis desdeEntorno() {
        String host = texto("redis.host", "localhost");
        int puerto = entero("redis.port", 6379, 1, 65535);
        String usuario = textoOpcional("redis.username");
        String contrasena = textoOpcional("redis.password");
        boolean ssl = booleano("redis.ssl", false);
        int baseDatos = entero("redis.database", 0, 0, 15);
        int tiempoConexionMs = entero("redis.connection-timeout-ms", 2000, 100, 60000);
        int tiempoSocketMs = entero("redis.socket-timeout-ms", 2000, 100, 60000);
        int maximoConexiones = entero("redis.pool.max-total", 16, 1, 1024);
        int maximoInactivas = entero("redis.pool.max-idle", 8, 0, 1024);
        int minimoInactivas = entero("redis.pool.min-idle", 1, 0, 1024);

        maximoInactivas = Math.min(maximoInactivas, maximoConexiones);
        minimoInactivas = Math.min(minimoInactivas, maximoInactivas);
        return new ConfiguracionRedis(host, puerto, usuario, contrasena, ssl, baseDatos,
                tiempoConexionMs, tiempoSocketMs, maximoConexiones, maximoInactivas, minimoInactivas);
    }

    public JedisPool crearPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maximoConexiones);
        poolConfig.setMaxIdle(maximoInactivas);
        poolConfig.setMinIdle(minimoInactivas);
        poolConfig.setBlockWhenExhausted(true);

        DefaultJedisClientConfig.Builder cliente = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(tiempoConexionMs)
                .socketTimeoutMillis(tiempoSocketMs)
                .database(baseDatos)
                .ssl(ssl);
        if (usuario != null) {
            cliente.user(usuario);
        }
        if (contrasena != null) {
            cliente.password(contrasena);
        }
        return new JedisPool(poolConfig, new HostAndPort(host, puerto), cliente.build());
    }

    public String host() {
        return host;
    }

    public int puerto() {
        return puerto;
    }

    public boolean ssl() {
        return ssl;
    }

    private static String texto(String clave, String predeterminado) {
        String valor = ConfigLoader.getProperty(clave);
        return valor == null || valor.isBlank() ? predeterminado : valor.trim();
    }

    private static String textoOpcional(String clave) {
        String valor = ConfigLoader.getProperty(clave);
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static int entero(String clave, int predeterminado, int minimo, int maximo) {
        String valor = ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < minimo || numero > maximo) {
                System.err.println("Valor fuera de rango para " + clave + "; se usará " + predeterminado + ".");
                return predeterminado;
            }
            return numero;
        } catch (NumberFormatException e) {
            System.err.println("Valor inválido para " + clave + "; se usará " + predeterminado + ".");
            return predeterminado;
        }
    }

    private static boolean booleano(String clave, boolean predeterminado) {
        String valor = ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        return Boolean.parseBoolean(valor.trim());
    }
}
