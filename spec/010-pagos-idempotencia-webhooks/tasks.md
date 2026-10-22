# Tasks 010 — Pagos idempotentes, webhooks durables y expiración

## Fase 1 — Estados y locks

- [x] `EstadoPago` con transiciones y estados terminales.
- [x] `Pago.transicionarA(...)` y `TransicionPagoInvalidaException`.
- [x] `IPagoRepository` con búsquedas por ID y locks `FOR UPDATE`.
- [x] `MySqlRepositorioPago` implementa locks y búsqueda del pendiente por venta.
- [x] `PagoService` valida venta `PENDIENTE` antes de reutilizar checkout.
- [x] `PagoService` lockea venta y pago en webhook y confirmación manual.
- [x] Pago tardío aprobado se conserva sin reactivar venta `CANCELADO/EXPIRADO`.
- [x] Tests de transiciones, replay y locks (`PagoTest`, `PagoServiceTest`).

## Fase 2 — Outbox

- [x] Dominio `EventoWebhook`, `EstadoEventoWebhook`, `ProveedorWebhook`.
- [x] `IEventoWebhookRepository` y `MySqlRepositorioEventoWebhook`.
- [x] `WebhookEventoService` con registro, reintentos y dead-letter.
- [x] `IProcesadorEventoWebhook` y `ProcesadorEventosWebhook` para MP/Stripe.
- [x] `WebhookService.procesarEvento(...)` y `StripeWebhookService.procesarEvento(...)`.
- [x] Stripe reconstruye la sesión desde el payload aun si el SDK no puede validar la versión de API.
- [x] Controllers persisten antes del `200`; error de persistencia responde `500`.
- [x] `JobWebhooks` con recuperación de eventos `PROCESANDO` antiguos.
- [x] Tests de Outbox, reintentos, dead-letter, persistencia previa al ACK y payload Stripe.
- [x] `mvn -q test` completo: 261 tests, 0 fallos, 0 errores, 4 omitidos.

## Fase 3 — Expiración

- [x] `EstadoVenta.EXPIRADO` y `Venta.Expirar()`.
- [x] `MySqlRepositorioVenta` restaura `fecha_compra`.
- [x] `IVentaRepository.buscarPendientesAnteriores(...)` e implementación MySQL.
- [x] `VentaService.expirarVentasPendientes(...)` con transacción por venta y liberación de stock.
- [x] `JobExpiracionVentas` y configuración.
- [x] Tests de expiración, no expiración de ventas pagadas y reintegro de stock.
- [x] Migración `migracion_pagos_webhooks_expiracion.sql`.

## Fase 4 — Integración y cierre

- [x] `ServerMain` cablea repositorios, servicios, jobs y shutdown.
- [x] `config.properties.example` con claves de Outbox y expiración.
- [x] `ConfigLoader` mapea `WEBHOOK_*` y `VENTAS_*`.
- [x] Documentación de decisión D-04/D-05 en auditoría.
- [x] `MEMORY.md` actualizado con las decisiones aprobadas.
- [x] Cierre documentado en `docs/auditoria/08-cierre-pagos-webhooks.md`.

## Pendientes operativos

- [ ] Aplicar la migración en la base local/despliegue antes de habilitar los jobs.
- [ ] Ejecutar los tests de integración MySQL; localmente se omiten por `Public Key Retrieval is not allowed`.
