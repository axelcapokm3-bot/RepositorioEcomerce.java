-- La tabla pagos ya contiene idempotency_key VARCHAR(64); esta migración no
-- modifica esa columna. La clave generada por PagoService cabe en 64 caracteres.
--
-- Antes de crear la restricción única, verificar que los valores existentes
-- de idempotency_key no contengan duplicados:
-- SELECT idempotency_key, COUNT(*) FROM pagos
-- WHERE idempotency_key IS NOT NULL
-- GROUP BY idempotency_key HAVING COUNT(*) > 1;

-- Ejecutar esta migración una sola vez, después de comprobar los duplicados.
ALTER TABLE ventas
    ADD COLUMN clave_idempotencia VARCHAR(255) NULL,
    ADD UNIQUE KEY uq_ventas_clave_idempotencia (clave_idempotencia);

ALTER TABLE pagos
    ADD COLUMN order_id VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    ADD UNIQUE KEY uq_pagos_idempotency_key (idempotency_key),
    ADD UNIQUE KEY uq_pagos_order_id (order_id);

INSERT INTO tipo_pago (nombre)
VALUES ('MERCADO_PAGO'), ('STRIPE'), ('TRANSFERENCIA')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
