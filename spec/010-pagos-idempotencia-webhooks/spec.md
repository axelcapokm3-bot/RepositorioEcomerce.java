# Spec 010 — Pagos idempotentes, webhooks durables y expiración de ventas

## Contexto

La auditoría de pagos identificó fallos de idempotencia, estados no monotónicos, ACK prematuro de webhooks y ventas pendientes sin expiración. Este spec cubre AL-01, AL-02, AL-03, AL-04 y AL-08.

## Decisiones aprobadas

- D-04: usar una tabla Outbox en MySQL para webhooks de Mercado Pago y Stripe.
- D-05: descontar stock al crear la venta y expirar ventas `PENDIENTE` con TTL configurable (30 minutos por defecto), liberando stock.
- Implementar con spec/plan y pruebas.

## Alcance

### AL-01 — Idempotencia y replay de checkout

- Una misma `Idempotency-Key` no debe crear otra venta.
- Un reintento no debe devolver un checkout reutilizable si la venta ya no está `PENDIENTE`.
- La venta y el pago deben validarse antes de reutilizar una operación externa.
- La restricción UNIQUE existente de `ventas.clave_idempotencia` se mantiene global.

### AL-02 — Estados y locks de pago

- `EstadoPago` define transiciones permitidas y terminales.
- `Pago` expone `transicionarA(...)` y rechaza retrocesos o transiciones inválidas.
- Las actualizaciones de webhook y confirmación manual lockean la venta y el pago con `SELECT ... FOR UPDATE`.
- Un pago aprobado tardío no reactiva una venta `CANCELADO` o `EXPIRADO`; se conserva para conciliación.
- Un webhook repetido no duplica la actualización ni el evento de venta.

### AL-03 — Outbox de webhooks

- Los controllers validan firma y persisten el evento crudo antes de responder 200.
- Tabla `eventos_webhook` con unique `(proveedor, tipo, evento_id)`, payload, estado, intentos y error.
- El procesamiento es asíncrono, con reintentos y estado `FALLIDO` al agotar intentos.
- Mercado Pago usa `type`/`data.id`; Stripe usa `Event.getId()`/`Event.getType()` y reconstruye el evento desde el payload.

### AL-04 — Fallos de procesamiento

- Si no se puede persistir el evento, el webhook responde 5xx y el proveedor puede reintentar.
- El procesamiento interno expone un método que propaga fallos; el job decide reintentar.
- No se responde 200 si el evento no quedó durablemente registrado.

### AL-08 — Expiración de ventas

- Job programado por lotes con TTL configurable.
- Cada venta se procesa en su propia transacción: lock, revalidación, `EXPIRADO`, liberación de stock.
- `EstadoVenta.EXPIRADO` es terminal y requiere migración del catálogo.
- Una venta pagada o cancelada no se expira.
- Un pago tardío aprobado se registra sin reactivar la venta expirada.

## Fuera de alcance

- AL-05 (URLs de retorno), AL-06 (passwords), AL-07 (CANJE), AL-09 (idempotencia por tenant).
- Cambios de contrato HTTP exitoso, identidad, cachés y validación de firma/URL de Stripe.
- Reembolsos automáticos de pagos tardíos.

## Criterios de aceptación

- Tests unitarios de transiciones, replay, locks, outbox y expiración pasan.
- `mvn -q test` compila y ejecuta la suite.
- Migración SQL aditiva documentada.
- No se introducen dependencias nuevas ni hilos manuales sin control.
- El ACK 200 ocurre solo después de persistir el evento en Outbox.
