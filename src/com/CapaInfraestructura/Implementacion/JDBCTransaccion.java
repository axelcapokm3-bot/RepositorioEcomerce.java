package com.CapaInfraestructura.Implementacion;

import java.sql.Connection;
import java.sql.SQLException;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaDominio.Excepciones.BusinessException;

public class JDBCTransaccion implements ITransaccion {

    @FunctionalInterface
    interface ProveedorConexion {
        Connection obtener() throws SQLException;
    }

    private final ProveedorConexion proveedorConexion;

    public JDBCTransaccion() {
        this(DatabaseConnection::getConnection);
    }

    JDBCTransaccion(ProveedorConexion proveedorConexion) {
        if (proveedorConexion == null) {
            throw new IllegalArgumentException("El proveedor de conexiones es obligatorio.");
        }
        this.proveedorConexion = proveedorConexion;
    }

    @Override
    public void ejecutarTransaccion(com.CapaAplicacion.Interfaces.OperacionTransaccional action) {
        try (Connection conexion = proveedorConexion.obtener()) {
            conexion.setAutoCommit(false);

            try {
                action.ejecutar(conexion);
                conexion.commit();
            } catch (Exception e) {
                try {
                    conexion.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
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