-- Migración aditiva para AL-03, AL-04 y AL-08.
-- Ejecutar una sola vez sobre la base existente.

INSERT INTO estado_venta (nombre)
VALUES ('EXPIRADO')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

CREATE TABLE IF NOT EXISTS eventos_webhook (
    id INT AUTO_INCREMENT PRIMARY KEY,
    proveedor VARCHAR(20) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    evento_id VARCHAR(255) NOT NULL,
    payload LONGTEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    intentos INT NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(1000) NULL,
    fecha_recepcion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    fecha_procesamiento DATETIME NULL,
    UNIQUE KEY uq_eventos_webhook (proveedor, tipo, evento_id),
    KEY idx_eventos_webhook_pendientes (estado, fecha_recepcion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
