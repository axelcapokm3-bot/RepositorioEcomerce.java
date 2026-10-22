package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IValidadorSesion;
import com.CapaDominio.Entidades.RolUsuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySqlValidadorSesion implements IValidadorSesion {

    private static final String SQL_ESTADO =
            "SELECT 1 FROM cuentas c "
                    + "JOIN cuentas_negocio cn ON cn.id_cuenta = c.id "
                    + "JOIN negocio n ON n.id = cn.id_negocio "
                    + "JOIN clientes cl ON cl.id = cn.id_cliente AND cl.id_negocio = cn.id_negocio "
                    + "WHERE c.id = ? AND c.activo = 1 "
                    + "AND cn.id_negocio = ? AND cn.id_cliente = ? AND cn.activo = 1 "
                    + "AND UPPER(cn.rol_usuario) = ? "
                    + "AND n.activo = 1 AND n.fecha_baja IS NULL "
                    + "AND cl.activo = 1 "
                    + "LIMIT 1";

    @Override
    public boolean estaVigente(long idCuenta, int idCliente, long idNegocio, RolUsuario rol) {
        if (idCuenta <= 0 || idCliente <= 0 || idNegocio <= 0 || rol == null) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL_ESTADO)) {
            statement.setLong(1, idCuenta);
            statement.setLong(2, idNegocio);
            statement.setInt(3, idCliente);
            statement.setString(4, rol.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo verificar el estado de la sesión en MySQL.", exception);
        }
    }
}
