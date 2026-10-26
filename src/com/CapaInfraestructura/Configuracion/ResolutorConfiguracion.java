package com.CapaInfraestructura.Configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

public final class ResolutorConfiguracion {
    private static final String ARCHIVO_LOCAL = "config.properties";
    private static final ResolutorConfiguracion INSTANCIA = crearInstancia();

    private final Function<String, String> propiedadesJava;
    private final Function<String, String> variablesEntorno;
    private final Function<String, String> propiedadesArchivo;
    private final Path archivoLocal;

    private ResolutorConfiguracion(
            Function<String, String> propiedadesJava,
            Function<String, String> variablesEntorno,
            Function<String, String> propiedadesArchivo,
            Path archivoLocal) {
        this.propiedadesJava = propiedadesJava;
        this.variablesEntorno = variablesEntorno;
        this.propiedadesArchivo = propiedadesArchivo;
        this.archivoLocal = archivoLocal;
    }

    public static ResolutorConfiguracion getInstancia() {
        return INSTANCIA;
    }

    public String obtener(String clave) {
        String valor = valorNoVacio(propiedadesJava.apply(clave));
        if (valor != null) {
            return valor;
        }

        String nombreVariable = obtenerNombreVariableEntorno(clave);
        if (nombreVariable != null) {
            valor = valorNoVacio(variablesEntorno.apply(nombreVariable));
            if (valor != null) {
                return valor;
            }
        }

        return valorNoVacio(propiedadesArchivo.apply(clave));
    }

    public String obtenerSecreto(String clave) {
        return obtener(clave);
    }

    public boolean esProduccion() {
        String entorno = obtener("entorno");
        if (entorno != null) {
            return "prod".equalsIgnoreCase(entorno.trim());
        }

        String perfil = obtener("app.perfil");
        return perfil != null && "prod".equalsIgnoreCase(perfil.trim());
    }

    public void validarArranque() {
        if (!Files.isRegularFile(archivoLocal)) {
            return;
        }
        if (!esProduccion()) {
            return;
        }
        throw new IllegalStateException(
                "No se permite config.properties local en producción. Archivo detectado: "
                        + archivoLocal.toAbsolutePath().normalize()
                        + ". Configure la aplicación solo con variables de entorno.");
    }

    public static String obtenerNombreVariableEntorno(String clave) {
        String explicit = switch (clave) {
            case "entorno" -> "ENTORNO";
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
            case "auth.bootstrap-token-expira-horas" -> "AUTH_BOOTSTRAP_TOKEN_EXPIRA_HORAS";
            case "auth.expiracion-horas" -> "AUTH_EXPIRACION_HORAS";
            case "app.perfil" -> "APP_PERFIL";
            case "app.frontend.url" -> "APP_FRONTEND_URL";
            case "app.backend.url" -> "APP_BACKEND_URL";
            case "app.cors.allowed-origins" -> "APP_CORS_ALLOWED_ORIGINS";
            case "app.trusted-proxies" -> "APP_TRUSTED_PROXIES";
            case "stripe.allowed-return-origins" -> "STRIPE_ALLOWED_RETURN_ORIGINS";
            case "stripe.connect-timeout-ms" -> "STRIPE_CONNECT_TIMEOUT_MS";
            case "stripe.read-timeout-ms" -> "STRIPE_READ_TIMEOUT_MS";
            default -> null;
        };
        if (explicit != null) {
            return explicit;
        }
        if (clave.startsWith("executor.")) {
            return "EXECUTOR_" + normalizarVariable(clave.substring("executor.".length()));
        }
        if (clave.startsWith("rate-limit.")) {
            return "RATE_LIMIT_" + normalizarVariable(clave.substring("rate-limit.".length()));
        }
        if (clave.startsWith("redis.")) {
            return "REDIS_" + normalizarVariable(clave.substring("redis.".length()));
        }
        if (clave.startsWith("webhook.")) {
            return "WEBHOOK_" + normalizarVariable(clave.substring("webhook.".length()));
        }
        if (clave.startsWith("ventas.")) {
            return "VENTAS_" + normalizarVariable(clave.substring("ventas.".length()));
        }
        if (clave.startsWith("negocio.")) {
            return "NEGOCIO_" + normalizarVariable(clave.substring("negocio.".length()));
        }
        return null;
    }

    static ResolutorConfiguracion paraPruebas(
            Function<String, String> propiedadesJava,
            Function<String, String> variablesEntorno,
            Function<String, String> propiedadesArchivo,
            Path archivoLocal) {
        return new ResolutorConfiguracion(
                propiedadesJava, variablesEntorno, propiedadesArchivo, archivoLocal);
    }

    private static ResolutorConfiguracion crearInstancia() {
        Path local = Path.of(ARCHIVO_LOCAL);
        Properties properties = new Properties();
        try {
            if (Files.isRegularFile(local)) {
                try (InputStream input = Files.newInputStream(local)) {
                    properties.load(input);
                }
            } else {
                try (InputStream input = ResolutorConfiguracion.class.getClassLoader()
                        .getResourceAsStream(ARCHIVO_LOCAL)) {
                    if (input != null) {
                        properties.load(input);
                    } else {
                        System.err.println(
                                "No se encontró config.properties; configure las propiedades mediante variables de entorno o system properties.");
                    }
                }
            }
        } catch (IOException exception) {
            System.err.println("Error al cargar config.properties: " + exception.getMessage());
        }

        return new ResolutorConfiguracion(
                System::getProperty,
                nombre -> nombre == null ? null : System.getenv(nombre),
                properties::getProperty,
                local);
    }

    private static String normalizarVariable(String sufijo) {
        return sufijo.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private static String valorNoVacio(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
