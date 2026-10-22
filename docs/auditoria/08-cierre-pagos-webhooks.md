# Cierre de pagos, webhooks durables y expiración

## 1. Alcance

Implementa AL-01, AL-02, AL-03, AL-04 y AL-08 de la auditoría de pagos, según
`spec/010-pagos-idempotencia-webhooks/`. No incluye AL-05, AL-06, AL-07 ni AL-09,
reembolsos automáticos, cambios de identidad, cachés ni el contrato exitoso de
checkout.

## 2. Decisiones implementadas

- **D-04**: Outbox MySQL para webhooks de Mercado Pago y Stripe. El evento se
  persiste antes de responder `200`; el procesamiento es asíncrono, con
  reintentos y estado `FALLIDO` al agotar intentos.
- **D-05**: el stock se descuenta al crear la venta. Las ventas `PENDIENTE`
  expiran con TTL configurable (30 minutos por defecto), liberan stock y pasan a
  `EXPIRADO`, que es terminal.
- **AL-02**: los estados de pago son monotónicos y las actualizaciones de webhook
  y confirmación manual bloquean venta y pago con `SELECT ... FOR UPDATE`.

## 3. Cambios principales

- `EstadoPago` y `Pago.transicionarA(...)`: transiciones `PENDIENTE` a
  `APROBADO/RECHAZADO/CANCELADO`; los estados terminales no retroceden.
- `PagoService.iniciarCheckout`: valida que la venta siga `PENDIENTE` antes de
  reutilizar un checkout externo.
- `PagoService.aplicarEstadoDePasarelaTransaccional`: lockea venta y pago,
  valida identidad y conserva pagos aprobados tardíos sin reactivar ventas
  `CANCELADO/EXPIRADO`.
- `IPagoRepository`/`MySqlRepositorioPago`: búsquedas con `FOR UPDATE`, incluida
  la del pago pendiente por venta.
- Outbox: `EventoWebhook`, `EstadoEventoWebhook`, `ProveedorWebhook`,
  `IEventoWebhookRepository`, `MySqlRepositorioEventoWebhook`,
  `WebhookEventoService`, `ProcesadorEventosWebhook` y `JobWebhooks`.
- `WebhookService` y `StripeWebhookService` exponen `procesarEvento(...)`, que
  propaga fallos reintentables/permanentes; `procesar(...)` conserva el camino
  tolerante para los llamadores existentes.
- Stripe reconstruye el `Event` desde el payload y, si el SDK no puede
  deserializar por diferencia de `api_version`, usa `deserializeUnsafe()` para
  obtener la `Session`; un payload inválido o contradictorio es permanente.
- `WebhookController` y `StripeWebhookController` reciben opcionalmente el
  servicio Outbox. Con Outbox: firma, persistencia, `200` y disparo asíncrono.
  Sin Outbox conservan el camino legacy para tests/compatibilidad.
- Expiración: `EstadoVenta.EXPIRADO`, `Venta.Expirar()`,
  `Venta.rehidratarFechaCompra(...)`, restauración de `fecha_compra` en MySQL,
  `IVentaRepository.buscarPendientesAnteriores(...)`,
  `VentaService.expirarVentasPendientes(...)` y `JobExpiracionVentas`.
- `ConfigLoader`, `config.properties.example` y `ServerMain`: configuración y
  ciclo de vida de los jobs, con cierre en el shutdown hook.

## 4. Migración

`migracion_pagos_webhooks_expiracion.sql` es aditiva:

- inserta `EXPIRADO` en `estado_venta`;
- crea `eventos_webhook` con unique `(proveedor, tipo, evento_id)` e índice por
  estado/fecha de recepción.

Debe aplicarse manualmente antes de habilitar los jobs. No hay migraciones
automáticas en el proyecto.

## 5. Verificación

Comandos ejecutados:

- `mvn -q test-compile`
- `mvn -q test`
- `mvn -q "-Dtest=PagoTest,EstadoVentaTest,PagoServiceTest,VentaServiceExpiracionTest,WebhookEventoServiceTest,StripeWebhookServiceTest,WebhookControllerOutboxTest,MySqlRepositorioEventoWebhookTest" test`

Resultado del suite completo: **261 tests, 0 fallos, 0 errores, 4 omitidos**.

Cobertura nueva o ajustada:

- transiciones válidas, mismo estado y rechazo de retrocesos terminales;
- replay de checkout con venta no pendiente;
- locks y pago tardío sin reactivación;
- expiración por lotes, revalidación, reintegro de stock y evento `EXPIRADA`;
- registro idempotente de Outbox, reintentos, dead-letter y error permanente;
- payload Stripe reconstruido desde JSON y fallback de deserialización;
- persistencia antes del `200`, `500` si falla la persistencia y `400` si el
  evento es inválido;
- SQL/mapeo de `MySqlRepositorioEventoWebhook`.

## 6. Riesgo residual

- Los tests de integración MySQL se omiten localmente por
  `PoolInitializationException: Public Key Retrieval is not allowed`; la
  migración y las consultas nuevas no se ejecutaron contra una base real.
- `MySqlRepositorioEventoWebhook.registrar` usa `INSERT IGNORE`; un error
  distinto de duplicado también devuelve `false` sin distinguirse.
- El constructor legacy de los controllers mantiene el ACK antes del
  procesamiento; producción debe usar el constructor con Outbox.
- `JobExpiracionVentas` usa un delay inicial igual al intervalo; no expira ventas
  inmediatamente al arrancar.
- No hay conciliación automática ni reembolso para pagos tardíos aprobados.
