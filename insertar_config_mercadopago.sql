-- Script SQL para insertar la configuración de Mercado Pago con valor vacío.
-- Usa el esquema configuracion_catalogo + configuracion_valor (multi-negocio),
-- consistente con MySqlNegocioRepository.listarConfiguracionesEfectivas().
-- Permite que el sistema inicie y opere de forma segura sin romper si no hay credenciales cargadas.

-- 1) Alta del catálogo de la clave (si no existe)
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES ('mercado_pago_access_token', 'string', 'Access token de Mercado Pago', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

-- 2) Valor efectivo por negocio (idNegocio 1 por defecto, vacío)
INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave = 'mercado_pago_access_token'
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);

-- 3) Secreto HMAC-SHA256 de Webhooks (copiar desde la configuración de Webhooks de MP).
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES ('mercado_pago_webhook_secret', 'string', 'Secreto HMAC para validar webhooks de Mercado Pago', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave = 'mercado_pago_webhook_secret'
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);

-- URL pública HTTPS de la pantalla de retorno del frontend (no usar localhost en producción).
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES ('checkout_return_url', 'string', 'URL pública de retorno del checkout de Mercado Pago', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave = 'checkout_return_url'
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);
