package com.CapaInfraestructura.Configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        Path localConfig = Path.of("config.properties");
        try {
            if (Files.isRegularFile(localConfig)) {
                try (InputStream input = Files.newInputStream(localConfig)) {
                    properties.load(input);
                }
            } else {
                try (InputStream input = ConfigLoader.class.getClassLoader()
                        .getResourceAsStream("config.properties")) {
                    if (input != null) {
                        properties.load(input);
                    } else {
                        System.err.println("No se encontró config.properties; configure las propiedades mediante variables de entorno o system properties.");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar config.properties: " + e.getMessage());
        }
    }

    private ConfigLoader() {
    }

    public static String getProperty(String key) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        value = properties.getProperty(key);
        if (value != null) {
            return value;
        }
        String environmentName = obtenerNombreVariableEntorno(key);
        return environmentName == null ? null : System.getenv(environmentName);
    }

    static String obtenerPropiedadJava(String key) {
        return valorNoVacio(System.getProperty(key));
    }

    static String obtenerVariableEntorno(String key) {
        String environmentName = obtenerNombreVariableEntorno(key);
        return environmentName == null ? null : valorNoVacio(System.getenv(environmentName));
    }

    static String obtenerPropiedadArchivo(String key) {
        return valorNoVacio(properties.getProperty(key));
    }

    private static String obtenerNombreVariableEntorno(String key) {
        String explicit = switch (key) {
            case "db.url" -> "ECOMMERCE_DB_URL";
            case "db.username" -> "ECOMMERCE_DB_USERNAME";
            case "db.password" -> "ECOMMERCE_DB_PASSWORD";
            case "db.maximumPoolSize" -> "ECOMMERCE_DB_POOL_SIZE";
            case "mercadopago.access-token" -> "MERCADOPAGO_ACCESS_TOKEN";
            case "mercadopago.public-base-url" -> "MERCADOPAGO_PUBLIC_BASE_URL";
            case "mercadopago.checkout-return-url" -> "MERCADOPAGO_CHECKOUT_RETURN_URL";
            case "mercadopago.webhook-secret" -> "MERCADOPAGO_WEBHOOK_SECRET";
            case "stripe.secret-key" -> "STRIPE_SECRET_KEY";
            case "stripe.success-url" -> "STRIPE_SUCCESS_URL";
            case "stripe.cancel-url" -> "STRIPE_CANCEL_URL";
            case "stripe.webhook-secret" -> "STRIPE_WEBHOOK_SECRET";
            case "auth.token-secret" -> "AUTH_TOKEN_SECRET";
            case "auth.bootstrap-token" -> "AUTH_BOOTSTRAP_TOKEN";
            case "auth.expiracion-horas" -> "AUTH_EXPIRACION_HORAS";
            case "app.perfil" -> "APP_PERFIL";
            case "app.cors.allowed-origins" -> "APP_CORS_ALLOWED_ORIGINS";
            default -> null;
        };
        if (explicit != null) {
            return explicit;
        }
        if (key.startsWith("rate-limit.")) {
            return "RATE_LIMIT_" + normalizarVariable(key.substring("rate-limit.".length()));
        }
        if (key.startsWith("redis.")) {
            return "REDIS_" + normalizarVariable(key.substring("redis.".length()));
        }
        if (key.startsWith("webhook.")) {
            return "WEBHOOK_" + normalizarVariable(key.substring("webhook.".length()));
        }
        if (key.startsWith("ventas.")) {
            return "VENTAS_" + normalizarVariable(key.substring("ventas.".length()));
        }
        if (key.startsWith("negocio.")) {
            return "NEGOCIO_" + normalizarVariable(key.substring("negocio.".length()));
        }
        return null;
    }

    private static String normalizarVariable(String sufijo) {
        return sufijo.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private static String valorNoVacio(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
