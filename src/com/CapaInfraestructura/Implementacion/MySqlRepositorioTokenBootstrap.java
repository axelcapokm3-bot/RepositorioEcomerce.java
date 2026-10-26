package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IRepositorioTokenBootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Token de bootstrap global (una sola fila, id=1). El valor en claro nunca se
 * almacena: solo su huella SHA-256.
 */
public class MySqlRepositorioTokenBootstrap implements IRepositorioTokenBootstrap {

    private static final String INSERTAR = "INSERT INTO tokens_bootstrap "
            + "(id, huella, expira_en, usado) VALUES (1, ?, ?, FALSE) "
            + "ON DUPLICATE KEY UPDATE id = id";

    private static final String RECLAMAR = "UPDATE tokens_bootstrap "
            + "SET usado = TRUE, usado_en = NOW() "
            + "WHERE id = 1 AND huella = ? AND usado = FALSE AND expira_en > NOW()";

    @Override
    public void sembrarSiAusente(String huella, LocalDateTime expiraEn) {
        exigirHuella(huella);
        if (expiraEn == null || !expiraEn.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("La expiración del token debe ser futura.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERTAR)) {
            statement.setString(1, huella);
            statement.setTimestamp(2, java.sql.Timestamp.valueOf(expiraEn));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo preparar el token de bootstrap.", exception);
        }
    }

    @Override
    public boolean reclamar(Connection conexion, String huella) {
        exigirHuella(huella);
        if (conexion == null) {
            throw new IllegalArgumentException("La conexión es obligatoria.");
        }
        try (PreparedStatement statement = conexion.prepareStatement(RECLAMAR)) {
            statement.setString(1, huella);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo validar el token de bootstrap.", exception);
        }
    }

    private static void exigirHuella(String huella) {
        if (huella == null || !huella.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("La huella del token de bootstrap no es válida.");
        }
    }
}
