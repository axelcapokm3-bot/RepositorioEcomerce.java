# PLAN — Checkout idempotente con Mercado Pago Orders API

## 1. Alcance e Impacto
- **Especificación:** `spec/001-orders-api-checkout-pro/spec.md` y este plan.
- **Impacto:** `HIGH` — afecta creación de ventas, persistencia de pagos, checkout externo y webhooks.
- La migración agrega `ventas.clave_idempotencia` y `pagos.order_id`, y restricciones únicas para evitar duplicados.
- `pagos.idempotency_key` ya existe como `VARCHAR(64)` en MySQL; no se vuelve a crear ni a ampliar.
- No se cambia la arquitectura por capas ni se implementa el frontend.

## 2. Arquitectura y Componentes Aprobados
- **Capas afectadas:** aplicación, dominio, infraestructura, presentación y composición en `ServerMain`.
- **Componentes aprobados:**
  - `VentaService` identifica la solicitud por `Idempotency-Key` y reutiliza venta/pago ante reintentos equivalentes.
  - `PagoService` persiste y reusa una clave de idempotencia estable, separada de la clave del cliente.
  - `PasarelaMercadoPago` crea Orders `online` con `processing_mode=manual` y utiliza `X-Idempotency-Key`.
  - La Order devuelve `checkout_url`; no se crea Preference ni se usa `init_point`.
  - Los webhooks consultan el recurso confirmado y validan referencia, monto y moneda.

## 3. Persistencia y Verificación
- `pagos.idempotency_key VARCHAR(64)` ya existe en el esquema compartido por el usuario y admite la clave estable generada (`venta-{id}-{tipo}`).
- `pagos.order_id` aún no existe en ese esquema; la migración lo agrega como `VARCHAR(255)` con índice único.
- `ventas.clave_idempotencia` se agrega con índice único para la clave enviada por el cliente.
- Revisar duplicados previos en `pagos.idempotency_key` antes de aplicar el índice único.
- Tests de servicio de venta y pago: `VentaServiceTest` y `PagoServiceTest`; verificación completa con `mvn test`.

## 4. Estrategia y Orden de Implementación
1. Recibir y validar el encabezado `Idempotency-Key`; persistir la clave en ventas y proteger venta/inventario con transacción.
2. Persistir el pago pendiente y una clave de pasarela única antes de llamar al proveedor.
3. Crear y persistir una Order de Mercado Pago y su `checkout_url`, reutilizando la clave estable en retries.
4. Conectar `PagoService` y los webhooks en `ServerMain`; validar la notificación contra los datos obtenidos desde el proveedor.
5. Aplicar la migración de base de datos después de revisar posibles duplicados de `pagos.idempotency_key`.
6. **Verificación:** `mvn test` (93 tests, 0 fallos, 0 errores).

## 5. Aprobación
- **Estado:** implementación y tests completados; la aplicación de la migración en una instancia MySQL es una acción aparte.
- **Decisiones vigentes:** Orders API exclusivamente, `processing_mode=manual`, claves API/pasarela separadas, confirmación de efectivo/transferencia interna.
- La decisión previa de Preference API quedó supersedida por la especificación aprobada posteriormente.
