-- Migración: tabla de cuentas para autenticación con roles.
-- Compatible con el modelo documentado en DocumentacionBaseDeDatos.txt (tabla cuentas).
-- Las contraseñas se guardan con hash PBKDF2 (formato pbkdf2$iteraciones$sal$hash), nunca en texto plano.
--
-- Segura para re-ejecutar: si la tabla ya existe no se modifica ni se borra nada
-- (CREATE TABLE IF NOT EXISTS + índices dentro de la definición).
-- Si la tabla YA existía con otro esquema, esta migración no la altera:
-- verificar columnas con: SHOW COLUMNS FROM cuentas;
-- Columnas requeridas por MySqlRepositorioCuenta: id, nombre_cuenta,
-- correo_electronico (UNIQUE), contrasenia, rol_usuario, id_cliente.

CREATE TABLE IF NOT EXISTS cuentas (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nombre_cuenta VARCHAR(100) NOT NULL,
    correo_electronico VARCHAR(150) NOT NULL,
    contrasenia VARCHAR(255) NOT NULL,
    rol_usuario VARCHAR(50) NOT NULL DEFAULT 'CLIENTE',
    token VARCHAR(255) NULL,
    id_cliente INT NOT NULL,
    saldo DECIMAL(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_cuentas_cliente FOREIGN KEY (id_cliente) REFERENCES clientes(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_cuentas_rol CHECK (rol_usuario IN ('ADMIN', 'CLIENTE')),
    CONSTRAINT chk_cuentas_saldo CHECK (saldo >= 0),
    UNIQUE KEY uq_cuentas_correo (correo_electronico),
    KEY idx_cuentas_cliente (id_cliente)
);
