# PLAN — Aislamiento por negocio de credenciales de Mercado Pago

## 1. Alcance e Impacto

- **Especificación:** `spec/005-credenciales-mercadopago/spec.md` y este plan.
- **Impacto:** `HIGH` — afecta cobros: una operación podría ejecutarse con la
  credencial de otro negocio bajo concurrencia.
- **Dentro de alcance:** reemplazar el token global por `MPRequestOptions` con
  `accessToken` en `PasarelaMercadoPago` y `WebhookService`; eliminar
  `MercadoPagoConfig.setAccessToken`; pruebas con dobles y captura de opciones.
- **Fuera de alcance:** optimización del fan-out por negocio en webhooks (P1,
  D-02), versiones del SDK, firma HMAC, cambios de esquema, Stripe.

## 2. Arquitectura y Componentes

- **CapaInfraestructura — `PasarelaMercadoPago`:**
  - `procesar`: construir `MPRequestOptions.builder()
    .accessToken(accessToken).customHeaders(headers).build()` y pasar el
    objeto a `orderClient.create(request, options)`. Eliminar
    `MercadoPagoConfig.setAccessToken(...)` (:91).
- **CapaAplicacion — `WebhookService`:**
  - `procesarOrderPorNegocio`: construir opciones con el token del negocio
    dueño y usarlas en `orderClient.get(orderId, options)` (:237).
  - `procesarPaymentMultiNegocio`: construir opciones con el token del negocio
    iterado y usarlas en `paymentClient.get(id, options)` (:211) y en la
    consulta de la Order derivada (:237).
  - Nuevo helper privado `opcionesPara(accessToken)` para no duplicar la
    construcción.
  - Quitar import de `MercadoPagoConfig` y dependencias muertas.
- **CapaInfraestructura — `StripeWebhookService`:** sin cambios.
- **CapaAplicacion — `ConfiguracionMercadoPago`:** sin cambios (RF-05).
- **Frontend:** sin cambios.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| `synchronized` + `setAccessToken` por llamada | Serializa todos los pagos del proceso y mantiene estado global mutable; una llamada HTTP lenta bloquea al resto |
| `ThreadLocal` con `setAccessToken` | El SDK no documenta soporte thread-scoped para ese estado estático; frágil ante pools y fallos |
| Una JVM por tenant | Costo operativo desproporcionado; no aplica a este backend |
| `MPRequestOptions.accessToken` por operación | **Elegida** (verificada con `javap` en 3.7.0) |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/com/CapaInfraestructura/Implementacion/PasarelaMercadoPago.java`
- `src/com/CapaAplicacion/Service/WebhookService.java`

### Archivos nuevos

- `test/java/com/CapaInfraestructura/Implementacion/PasarelaMercadoPagoCredencialesTest.java`
- `test/java/com/CapaAplicacion/Service/WebhookServiceCredencialesTest.java`

### Dependencias con otras especificaciones

- `spec/007-checkout-url-pago`: 007 decide cuándo redirigir al `urlPago` que
  produce esta pasarela; ambas tocan Mercado Pago pero en capas distintas.
- `spec/004-stock-concurrencia`: sin solapamiento de archivos.

## 4. Estrategia y Orden de Implementación

1. `PasarelaMercadoPago`: opciones por operación (RF-02).
2. `WebhookService`: helper + opciones por negocio (RF-03/RF-04).
3. Grep de control: `setAccessToken` en 0; sin secretos en logs (RF-01, RF-06).
4. Tests con dobles: capturar `MPRequestOptions` y verificar el token exacto
   por negocio; secuencia de dos negocios sin arrastre; sin pagos reales.
5. Verificación: `mvn -Dtest=...` dirigido + `node` no aplica (solo Java).

### Riesgos de regresión

- El SDK podría requerir además `MercadoPagoConfig` inicializado; se
  verificarán las firmas con `javap` antes y después (ya verificado:
  `MPRequestOptions` existe con `accessToken`).
- Los tests existentes de firma de webhook no dependen del token global y
  deben seguir pasando.

### Estrategia de pruebas

- **Unitarias (obligatorias, sin red):** mock de `OrderClient`/`PaymentClient`
  lanzando una excepción controlada tras capturar las opciones; assert de
  `options.getAccessToken()` por negocio; dos tokens distintos en secuencia;
  validación de no exponer token en `rawResponse`/mensajes.
- **Regresión:** suite completa de `WebhookService`/`PagoService`.
- **Integración real:** no se ejecutará ninguna llamada a Mercado Pago (medio
  de pago real).

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (implementación
  solicitada el 2026-10-19; se adoptan las opciones recomendadas).
- **Decisiones bloqueantes:** D-01 y D-02 de `spec.md` §7 — resueltas.
- **Decisión del usuario y fecha:** "implementa cuando lo tengas armado",
  2026-10-19.
