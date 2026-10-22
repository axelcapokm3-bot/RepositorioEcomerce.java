# Plan 010 — Implementación

## Fase 1 — Estados y locks

1. `EstadoPago`: transiciones `PENDIENTE -> APROBADO|RECHAZADO|CANCELADO`; estados terminales sin salida.
2. `Pago.transicionarA(...)` con validación de estado y `puedeTransicionarA(...)`.
3. `IPagoRepository`: `buscarPorId(int)`, `buscarPorIdParaUpdate(int, Connection)`, `buscarPendientePorVentaParaUpdate(long, int, Connection)`.
4. `MySqlRepositorioPago`: implementar las consultas con `FOR UPDATE`.
5. `PagoService`:
   - validar `PENDIENTE` antes de reutilizar checkout;
   - lockear venta y pago en la transacción;
   - aplicar transiciones y conservar pagos tardíos sin reactivar ventas;
   - lockear el pago en confirmación manual.
6. Tests de transiciones, replay y locks.

## Fase 2 — Outbox

1. Dominio: `EventoWebhook`, `EstadoEventoWebhook`, `ProveedorWebhook`.
2. Puerto `IEventoWebhookRepository` y `MySqlRepositorioEventoWebhook`.
3. `WebhookEventoService`: registrar, procesar pendientes, reintentar y agotar intentos.
4. `IProcesadorEventoWebhook` y `ProcesadorEventosWebhook` (MP/Stripe).
5. `WebhookService.procesarEvento(...)` y `StripeWebhookService.procesarEvento(...)` que propagan fallos.
6. Controllers: persistir antes del 200; 500 si falla la persistencia; disparar procesamiento async.
7. Job `JobWebhooks` con `ScheduledExecutorService` y configuración.
8. Tests del servicio Outbox y del procesador.

## Fase 3 — Expiración

1. `EstadoVenta.EXPIRADO` y `Venta.Expirar()`.
2. Migración `estado_venta` y tabla Outbox.
3. `IVentaRepository.buscarPendientesAnteriores(...)` e implementación MySQL.
4. `VentaService.expirarVentasPendientes(...)` con transacción por venta y liberación de stock.
5. Job `JobExpiracionVentas` y configuración.
6. Tests de expiración y no reactivación.

## Fase 4 — Integración y cierre

1. `ServerMain`: wiring de repos, servicios, jobs y shutdown.
2. `config.properties.example`: TTL e intervalos.
3. `mvn -q test`.
4. Actualizar documentación de cierre y memoria.

## Migración

```sql
INSERT INTO estado_venta (nombre) VALUES ('EXPIRADO')
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
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  fecha_procesamiento DATETIME NULL,
  UNIQUE KEY uq_eventos_webhook (proveedor, tipo, evento_id),
  KEY idx_eventos_webhook_pendientes (estado, fecha_recepcion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Riesgos

- Si la migración de `EXPIRADO` no se aplica, el job fallará al persistir.
- El procesamiento de un evento Stripe requiere round-trip de `Event`; se valida con test.
- Los tests MySQL siguen dependiendo de una base local disponible.
