package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IRevocacionToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class RevocacionTokenMySql implements IRevocacionToken {

    private static final String INSERTAR =
            "INSERT INTO tokens_revocados (huella, id_cuenta, motivo, expira_en) "
                    + "VALUES (?, ?, 'LOGOUT', ?) "
                    + "ON DUPLICATE KEY UPDATE id_cuenta = VALUES(id_cuenta), "
                    + "motivo = VALUES(motivo), expira_en = VALUES(expira_en), "
                    + "fecha_revocacion = CURRENT_TIMESTAMP";

    private static final String CONSULTAR =
            "SELECT 1 FROM tokens_revocados WHERE huella = ? AND expira_en > NOW() LIMIT 1";

    @Override
    public void revocar(String token, long idCuenta, long expiraEnEpochSegundos) {
        if (idCuenta <= 0) {
            throw new IllegalArgumentException("El ID de cuenta debe ser mayor a cero.");
        }
        if (expiraEnEpochSegundos <= 0) {
            throw new IllegalArgumentException("La expiración del token debe ser positiva.");
        }
        String huella = HuellaToken.calcular(token);
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERTAR)) {
            statement.setString(1, huella);
            statement.setLong(2, idCuenta);
            statement.setTimestamp(3, Timestamp.from(Instant.ofEpochSecond(expiraEnEpochSegundos)));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo registrar la revocación del token.", exception);
        }
    }

    @Override
    public boolean estaRevocado(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String huella = HuellaToken.calcular(token);
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CONSULTAR)) {
            statement.setString(1, huella);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo verificar la revocación del token.", exception);
        }
    }
}
