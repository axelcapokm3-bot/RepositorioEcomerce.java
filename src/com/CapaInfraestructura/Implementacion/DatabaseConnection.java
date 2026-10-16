package com.CapaInfraestructura.Implementacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DatabaseConnection {
    private static final HikariDataSource DATA_SOURCE = createDataSource();

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

private static HikariDataSource createDataSource() {
    try {
        Properties properties = loadProperties();
        HikariConfig config = new HikariConfig();
        
        config.setJdbcUrl(exigirValor(properties, "db.url", "ECOMMERCE_DB_URL"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(exigirValor(properties, "db.username", "ECOMMERCE_DB_USERNAME"));
        config.setPassword(exigirValor(properties, "db.password", "ECOMMERCE_DB_PASSWORD"));
        config.setMaximumPoolSize(Integer.parseInt(value(properties, "db.maximumPoolSize", "ECOMMERCE_DB_POOL_SIZE", "10")));
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setPoolName("ecommerce-mysql-pool");

        return new HikariDataSource(config);
    } catch (Exception e) {
        System.err.println("❌ ERROR CRÍTICO AL INITIALIZAR HIKARICP:");
        e.printStackTrace();
        throw new ExceptionInInitializerError("Fallo la conexión con la Base de Datos: " + e.getMessage());
    }
}
    private static Properties loadProperties() {
        Properties properties = new Properties();
        Path externalConfig = Path.of("config.properties");
        try {
            if (Files.exists(externalConfig)) {
                try (InputStream input = Files.newInputStream(externalConfig)) {
                    properties.load(input);
                }
            } else {
                try (InputStream input = DatabaseConnection.class.getClassLoader()
                        .getResourceAsStream("config.properties")) {
                    if (input != null) {
                        properties.load(input);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer config.properties.", exception);
        }
        return properties;
    }

    private static String exigirValor(Properties properties, String property, String environment) {
        String valor = value(properties, property, environment, null);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta la configuración '" + property + "'. Defínala en config.properties o en la variable "
                            + environment + ". No se permiten credenciales predeterminadas.");
        }
        return valor;
    }

    private static String value(Properties properties, String property, String environment, String defaultValue) {
        String configured = properties.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        configured = System.getenv(environment);
        return configured == null || configured.isBlank() ? defaultValue : configured.trim();
    }
}