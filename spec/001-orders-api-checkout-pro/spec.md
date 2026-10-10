# SPEC — Checkout de Mercado Pago mediante Orders API

## 1. Objetivo

Crear un checkout de Mercado Pago con Orders API, sin generar Preferences, y
proteger la creación de ventas y de operaciones de pago frente a reintentos.

## 2. Flujo aprobado

1. `POST /api/ventas/crear` exige un encabezado `Idempotency-Key` no vacío.
2. `VentaService` busca esa clave antes de crear una venta. Si ya existe,
   valida que cliente, tipo de pago, total y detalles coincidan y reutiliza la
   venta y el pago. Si el contenido difiere, rechaza la solicitud.
3. La clave de solicitud se guarda en `ventas.clave_idempotencia`; su índice
   único evita crear ventas duplicadas ante solicitudes concurrentes. El
   descuento de inventario y la inserción de la venta se realizan en una
   transacción.
4. Para Mercado Pago, `PagoService` guarda primero un pago pendiente con una
   clave estable propia de la pasarela en `pagos.idempotency_key`.
5. `PasarelaMercadoPago` crea una Order `online` con `processing_mode=manual`,
   productos, monto y moneda, `external_reference` igual al ID de venta y
   configuración de retorno. Envía la clave persistida mediante
   `X-Idempotency-Key`.
6. La respuesta persiste `order.id` y `checkout_url`. Ante un timeout o reintento
   se reutiliza el mismo pago y la misma clave de pasarela.
7. El webhook consulta la Order en Mercado Pago y valida referencia externa,
   importe y moneda antes de actualizar el pago y la venta. También admite
   notificaciones de pago individual asociadas a una Order.

Las claves API y de pasarela son distintas y se persisten en columnas
independientes. La clave de pasarela no se regenera durante un retry.

## 3. Reglas de negocio

- El checkout público admite `MERCADO_PAGO` y `STRIPE`.
- Efectivo y transferencia permanecen pendientes hasta una confirmación interna;
  no se publica un endpoint para confirmarlos.
- El estado de la venta solo cambia a pagada después de confirmar el pago.
- Para Mercado Pago, Orders API es el único flujo de checkout. No se usa
  `PreferenceClient`, `init_point` ni `merchant_order`.
- La URL de retorno de Orders y el token se obtienen de la configuración ya
  definida para el negocio. El checkout devuelve y persiste `checkout_url`.

## 4. Criterios de aceptación

- [ ] Rechaza la creación si `Idempotency-Key` falta, está vacío o supera 255
  caracteres.
- [ ] Reutiliza la misma venta y el mismo pago con la misma clave y el mismo
  contenido.
- [ ] Rechaza el uso de una clave de solicitud existente con contenido distinto.
- [ ] Una clave de solicitud y una clave de pasarela quedan persistidas de forma
  independiente; la segunda se envía sin cambios en cada retry a Mercado Pago.
- [ ] La venta y el inventario se guardan dentro de una transacción antes de
  iniciar el checkout.
- [ ] La creación de la Order incluye `external_reference`, ítems, monto,
  configuración de retorno y `processing_mode=manual`; persiste su ID y URL.
- [ ] Los webhooks aceptan IDs de Order no numéricos y verifican firma y datos
  consultados directamente al proveedor.
- [ ] Los tests y la compilación Maven pasan.

## 5. Decisiones aprobadas y cambios

| Decisión | Estado |
|---|---|
| Migrar Mercado Pago a Orders API exclusivamente y no crear Preferences | Aprobada por el usuario, 2026-10-08 |
| Exigir `Idempotency-Key` para crear ventas | Aprobada por el usuario |
| Persistir por separado la clave de solicitud y la clave de pasarela | Aprobada por el usuario |
| Mantener la confirmación manual de efectivo/transferencia interna, sin endpoint público | Aprobada por el usuario |

La decisión anterior de usar Preference API y `init_point`, documentada en la
versión previa de esta especificación, queda supersedida.
