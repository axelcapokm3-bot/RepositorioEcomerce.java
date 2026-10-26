package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.SQLException;

import com.CapaInfraestructura.Configuracion.DiagnosticoArranque;
import com.CapaInfraestructura.Configuracion.ResolutorConfiguracion;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DatabaseConnection {
    private static final ResolutorConfiguracion RESOLUTOR = ResolutorConfiguracion.getInstancia();
    private static final HikariDataSource DATA_SOURCE = createDataSource();

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    private static HikariDataSource createDataSource() {
        try {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl(exigirValor("db.url"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(exigirValor("db.username"));
            config.setPassword(exigirValor("db.password"));
            config.setMaximumPoolSize(entero("db.maximumPoolSize", 10));
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10_000);
            config.setPoolName("ecommerce-mysql-pool");

            return new HikariDataSource(config);
        } catch (Exception e) {
            DiagnosticoArranque.registrar("startup.fail", "DatabaseConnection", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String exigirValor(String clave) {
        String valor = RESOLUTOR.obtener(clave);
        if (valor == null || valor.isBlank()) {
            String variable = ResolutorConfiguracion.obtenerNombreVariableEntorno(clave);
            throw new IllegalStateException(
                    "Falta la configuración '" + clave + "'. Defínala mediante system property, "
                            + "variable de entorno " + variable
                            + " o config.properties local (solo desarrollo). "
                            + "No se permiten credenciales predeterminadas.");
        }
        return valor.trim();
    }

    private static int entero(String clave, int predeterminado) {
        String valor = RESOLUTOR.obtener(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        return Integer.parseInt(valor.trim());
    }
}
