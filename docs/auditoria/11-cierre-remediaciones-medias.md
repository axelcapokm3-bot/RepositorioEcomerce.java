# Cierre de remediaciones medias (ME-01 a ME-13)

## Alcance

Implementación de las 13 remediaciones medias acordadas para el backend
eCommerce. No se modificaron los flujos de pago ya cerrados en bloques
anteriores, salvo los endurecimientos explícitos de esta lista.

## Cambios por remediación

| ID | Cambio |
|---|---|
| ME-01 | `ManejadorExcepcionesHttp` centraliza el mapeo HTTP; SQL 1062 se traduce a `IdentidadDuplicadaException` (409); actualizar/eliminar producto inexistente devuelve 404; registro limitado a 5 intentos por correo cada 15 minutos. |
| ME-02 | Token de bootstrap administrativo de un solo uso persistido como SHA-256 en `tokens_bootstrap`. El consumo ocurre dentro de la transacción de registro; expira según `auth.bootstrap-token-expira-horas` (24 h por defecto). Sin base disponible se usa un fallback en memoria. |
| ME-03 | `SanitizadorLog` enmascara correos, tokens Bearer/JWT, credenciales clave-valor, tarjetas válidas por Luhn y limita a 500 caracteres. Se aplica en controladores, servicios, webhooks, auditoría, conexión y executor. |
| ME-04 | `DireccionClienteResolver` obtiene la IP real con `X-Forwarded-For` recorrido de derecha a izquierda y `X-Real-IP`, saltando únicamente proxies de `app.trusted-proxies`. |
| ME-05 | `ResolutorNegocio` valida el slug con formato estricto, elimina el fallback silencioso al `Host` cuando hay header explícito inválido y `BaseController` responde 400/403 según corresponda. |
| ME-06 | `ValidadorUrlRetorno` exige URL absoluta, sin credenciales ni fragmento, y allowlist de origen (`stripe.allowed-return-origins`, fallback `app.frontend.url`). En producción exige HTTPS público y allowlist; en desarrollo permite localhost HTTP. Rechaza localhost, IP privadas y orígenes no autorizados. |
| ME-07 | La firma de Mercado Pago valida frescura de `ts` contra `webhook.firma-tolerancia-segundos` (300 s por defecto) antes de calcular el HMAC. Los tests usan timestamps dinámicos y hay regresión para firma vencida. Stripe queda cubierto por la tolerancia de 300 s del SDK y la deduplicación por `evento_id`. |
| ME-08 | `MySqlRepositorioVenta` actualiza la venta antes de borrar sus detalles y crea con `getGeneratedKeys` en el camino de inserción. |
| ME-09 | Manejador explícito de excepciones HTTP con respuestas consistentes y registro sanitizado. |
| ME-10 | Auditoría con `id_negocio`: columna, índice, filtros, inserción, mapeo, backfill histórico y filtro en memoria compatible. Las filas sin negocio resoluble no se exponen en consultas por negocio. |
| ME-11 | `scripts/scan-secrets.ps1` escanea solo archivos trackeados por git y reporta ruta/línea sin imprimir valores. |
| ME-12 | Stripe usa `RequestOptions` con `stripe.connect-timeout-ms` (5000) y `stripe.read-timeout-ms` (15000); el checkout limita a 10 solicitudes por negocio y ventana (`rate-limit.checkout.negocio`). |
| ME-13 | `PasarelaStripe` acepta un `CreadorSesion` inyectable. `PasarelaStripeTest` verifica URL de retorno, allowlist, metadata, importes, idempotencia y timeouts sin llamar a Stripe. |

## Migraciones y configuración

- `migracion_auditoria_negocio.sql`: agrega `id_negocio`, índice
  `idx_auditoria_negocio_fecha` y backfill desde ventas, productos, clientes y
  pagos de forma idempotente.
- `migracion_token_bootstrap.sql`: crea `tokens_bootstrap` con fila única
  global (`id = 1`).
- `config.properties.example`: documenta `stripe.allowed-return-origins`,
  `stripe.connect-timeout-ms`, `stripe.read-timeout-ms`,
  `rate-limit.checkout.negocio` y `rate-limit.checkout.ventana-segundos`.
- `DocumentacionBaseDeDatos.txt`: esquema actualizado de `auditoria` y nueva
  tabla `tokens_bootstrap`.

## Verificación

- Compilación: `mvn -q -DskipTests compile` correcta.
- Suite completa: `mvn -q test` → **334 tests, 0 fallos, 0 errores, 1 omitido**
  (`PasarelaMercadoPagoTest`, por ausencia de credenciales).
- Pruebas nuevas o ampliadas: `ManejadorExcepcionesHttpTest`,
  `SanitizadorLogTest`, `DireccionClienteResolverTest`,
  `ValidadorUrlRetornoTest`, `HuellaTokenBootstrapTest`, `PasarelaStripeTest`,
  `RepositorioAuditoriaTest`, más casos en `AutenticacionServiceTest`,
  `ProductoServiceTest`, `ResolutorNegocioTest`, `WebhookServiceTest`,
  `WebhookServiceFanoutTest`, `WebhookControllerTest`,
  `WebhookControllerOutboxTest` y `VentaControllerTenantTest`.
- Escaneo de secretos: `scripts/scan-secrets.ps1` → sin hallazgos en 541
  archivos trackeados.
- No se ejecutaron pagos reales ni pruebas contra sandbox de Stripe o Mercado
  Pago; ME-13 usa un creador de sesiones falso.

## Observaciones

- El listado actual renumera ME-05, ME-07, ME-08, ME-09, ME-10 y ME-11 frente a
  la auditoría original. Este cierre sigue el listado explícito del usuario.
- Persisten los hallazgos críticos/altos de otros bloques y las decisiones
  diferidas (por ejemplo, multi-cuenta de Mercado Pago). Este documento no
  cambia el veredicto global de producción.
