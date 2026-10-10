-- Script SQL para insertar la configuración de Stripe con valor vacío.
-- Usa el esquema configuracion_catalogo + configuracion_valor (multi-negocio),
-- consistente con MySqlNegocioRepository.listarConfiguracionesEfectivas().
-- Permite iniciar el sistema sin romper si no hay credenciales cargadas.

-- 1) Alta del catálogo de la clave (si no existe)
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES ('stripe_secret_key', 'string', 'Secret key de Stripe (sk_test/sk_live)', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

-- 2) Valor efectivo por negocio (idNegocio 1 por defecto, vacío)
INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave = 'stripe_secret_key'
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);

-- Secreto del endpoint webhook, obtenido desde Stripe Dashboard > Developers > Webhooks.
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES ('stripe_webhook_secret', 'string', 'Signing secret del endpoint webhook de Stripe', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave = 'stripe_webhook_secret'
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);

-- URLs públicas del frontend. La URL de éxito debe conservar el placeholder de Stripe.
INSERT INTO configuracion_catalogo (clave, tipo, descripcion, valor_default, categoria)
VALUES
    ('stripe_success_url', 'string', 'URL de retorno para checkout exitoso; debe incluir {CHECKOUT_SESSION_ID}', '', 'pagos'),
    ('stripe_cancel_url', 'string', 'URL de retorno si se cancela Checkout', '', 'pagos')
ON DUPLICATE KEY UPDATE clave = clave;

INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion)
SELECT 1, cc.id, '', NOW()
FROM configuracion_catalogo cc
WHERE cc.clave IN ('stripe_success_url', 'stripe_cancel_url')
ON DUPLICATE KEY UPDATE id_configuracion = VALUES(id_configuracion);
