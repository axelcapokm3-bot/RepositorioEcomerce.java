package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.SQLException;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaDominio.Excepciones.BusinessException;

public class JDBCTransaccion implements ITransaccion {

    @Override
    public void ejecutarTransaccion(com.CapaAplicacion.Interfaces.OperacionTransaccional action) {
        try (Connection conexion = DatabaseConnection.getConnection()) {
            conexion.setAutoCommit(false);

            try {
                action.ejecutar(conexion);
                conexion.commit();
            } catch (Exception e) {
                conexion.rollback(); 
                if (e instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new RuntimeException("Transacción abortada debido a un error: " + e.getMessage(), e);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en la conexión o gestión de la base de datos", e);
        }
    }
}