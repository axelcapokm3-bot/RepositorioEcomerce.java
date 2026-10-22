-- La tabla pagos ya contiene idempotency_key VARCHAR(64); esta migración no
-- modifica esa columna. La clave generada por PagoService cabe en 64 caracteres.
--
-- Antes de crear la restricción única, verificar que los valores existentes
-- de idempotency_key no contengan duplicados:
-- SELECT idempotency_key, COUNT(*) FROM pagos
-- WHERE idempotency_key IS NOT NULL
-- GROUP BY idempotency_key HAVING COUNT(*) > 1;

-- La idempotencia de ventas es por negocio: la misma clave puede repetirse en
-- dos negocios distintos, pero no dentro del mismo negocio.
-- Verificar duplicados antes de aplicar el índice:
-- SELECT id_negocio, clave_idempotencia, COUNT(*) FROM ventas
-- WHERE clave_idempotencia IS NOT NULL
-- GROUP BY id_negocio, clave_idempotencia HAVING COUNT(*) > 1;

-- Si la instalación todavía tiene el índice global anterior, eliminarlo antes
-- de crear el compuesto:
-- ALTER TABLE ventas DROP INDEX uq_ventas_clave_idempotencia;

-- Ejecutar esta migración una sola vez, después de comprobar los duplicados.
ALTER TABLE ventas
    ADD COLUMN clave_idempotencia VARCHAR(255) NULL,
    ADD UNIQUE KEY uq_ventas_negocio_idempotencia (id_negocio, clave_idempotencia);

ALTER TABLE pagos
    ADD COLUMN order_id VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    ADD UNIQUE KEY uq_pagos_idempotency_key (idempotency_key),
    ADD UNIQUE KEY uq_pagos_order_id (order_id);

INSERT INTO tipo_pago (nombre)
VALUES ('MERCADO_PAGO'), ('STRIPE'), ('TRANSFERENCIA')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
