-- ME-10: aislamiento de auditoría por negocio.
-- Agrega id_negocio a la auditoría para filtrar sin depender de EXISTS sobre
-- ventas/productos/clientes/pagos. Las filas históricas se completan cuando la
-- entidad referenciada todavía existe; las no resolubles quedan en NULL y no
-- aparecen en consultas por negocio.
-- Script idempotente: no falla si el esquema ya fue migrado.

DROP PROCEDURE IF EXISTS migrar_auditoria_negocio;

DELIMITER $$

CREATE PROCEDURE migrar_auditoria_negocio()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'auditoria'
          AND COLUMN_NAME = 'id_negocio'
    ) THEN
        ALTER TABLE auditoria
            ADD COLUMN id_negocio BIGINT NULL AFTER id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'auditoria'
          AND INDEX_NAME = 'idx_auditoria_negocio_fecha'
    ) THEN
        CREATE INDEX idx_auditoria_negocio_fecha ON auditoria (id_negocio, fecha);
    END IF;

    UPDATE auditoria a
    JOIN ventas v ON a.entidad = 'Venta' AND v.id = a.id_entidad
    SET a.id_negocio = v.id_negocio
    WHERE a.id_negocio IS NULL;

    UPDATE auditoria a
    JOIN productos p ON a.entidad = 'Producto' AND p.id = a.id_entidad
    SET a.id_negocio = p.id_negocio
    WHERE a.id_negocio IS NULL;

    UPDATE auditoria a
    JOIN clientes c ON a.entidad = 'Cliente' AND c.id = a.id_entidad
    SET a.id_negocio = c.id_negocio
    WHERE a.id_negocio IS NULL;

    UPDATE auditoria a
    JOIN pagos pg ON a.entidad = 'Pago' AND pg.id = a.id_entidad
    SET a.id_negocio = pg.id_negocio
    WHERE a.id_negocio IS NULL;
END$$

DELIMITER ;

CALL migrar_auditoria_negocio();
DROP PROCEDURE IF EXISTS migrar_auditoria_negocio;
