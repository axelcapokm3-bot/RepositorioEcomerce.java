# SPEC — Aislamiento por negocio de credenciales de Mercado Pago

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- El proyecto usa el SDK `com.mercadopago:sdk-java:3.7.0` (`pom.xml:36-37`,
  artefacto verificado en `.m2`).
- `MercadoPagoConfig` expone `accessToken` como estado **estático global**
  (`javap` sobre `sdk-java-3.7.0.jar`: `public static void setAccessToken(String)`
  / `getAccessToken()`).
- `PasarelaMercadoPago.procesar` muta ese estado global antes de crear la
  orden: `MercadoPagoConfig.setAccessToken(accessToken)`
  (`PasarelaMercadoPago.java:91`) y luego
  `orderClient.create(request, options)` (:97) con `MPRequestOptions` que solo
  lleva headers.
- `WebhookService` muta el mismo estado global:
  - `procesarOrderPorNegocio`: `setAccessToken` del negocio dueño (:195) y
    luego `orderClient.get(orderId)` **sin opciones** (:237).
  - `procesarPaymentMultiNegocio`: `setAccessToken` por cada negocio activo
    (:210) y luego `paymentClient.get(...)` **sin opciones** (:211).
- Los clientes `PaymentClient`/`OrderClient` son instancias de larga vida
  (`WebhookService.java:50-51`, `PasarelaMercadoPago.java:44`) usadas desde el
  pool de hasta 200 hilos (`ServerMain`), por lo que dos requests concurrentes
  de negocios distintos pueden pisarse el token global entre el `set` y la
  llamada HTTP: la operación sale con credenciales de otro tenant.
- Resolución de credenciales: `ConfiguracionMercadoPago` resuelve por negocio
  con precedencia propiedades Java → variables de entorno → archivo → catálogo
  del negocio (`ConfiguracionMercadoPago.java:43-49, 51-76`), claves
  `mercadopago.access-token` / `mercado_pago_access_token`.
- El SDK 3.7.0 **sí** ofrece credencial por operación: `MPRequestOptions`
  tiene `accessToken` con builder `.accessToken(...)` (verificado por `javap`)
  y `OrderClient.create(OrderCreateRequest, MPRequestOptions)`,
  `OrderClient.get(String, MPRequestOptions)` y
  `PaymentClient.get(Long, MPRequestOptions)` existen (verificados por
  `javap`).

### 1.2 Discrepancias con el informe previo

El informe advertía el riesgo de estado global. La inspección lo confirma y
además demuestra que la alternativa sin estado global **es viable** con la API
real del SDK (no hace falta sincronizar ni aislar por JVM).

## 2. Objetivo

Que cada operación contra Mercado Pago use exclusivamente el access token del
negocio correspondiente, sin estado global mutable compartido, preservando la
validación de webhooks, los contratos de `ResultadoPago` y sin exponer
credenciales en logs ni respuestas.

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: ninguna operación de pago puede ejecutarse con el token de otro
  negocio.
- I-02: los secretos de firma y los tokens no se registran en logs ni se
  devuelven en respuestas ni en `rawResponse`.
- I-03: la validación de firma de webhooks y las validaciones de
  importe/moneda/referencia permanecen intactas.

### 3.2 Requisitos funcionales

- RF-01: eliminar toda invocación de `MercadoPagoConfig.setAccessToken(...)` del
  código de producción (grep debe quedar en 0 coincidencias).
- RF-02: `PasarelaMercadoPago.procesar` construye
  `MPRequestOptions.builder().accessToken(token).customHeaders(headers).build()`
  y lo pasa a `orderClient.create(request, options)`.
- RF-03: `WebhookService.procesarOrderPorNegocio` propaga el token del negocio
  del pago mediante `MPRequestOptions` a `orderClient.get(orderId, options)`.
- RF-04: `WebhookService.procesarPaymentMultiNegocio` pasa el token del negocio
  iterado mediante `MPRequestOptions` a `paymentClient.get(id, options)` y a
  la posterior consulta de la Order.
- RF-05: token ausente o vacío → comportamiento actual: `validarConfiguracion`
  lanza `PasarelaNoDisponibleException`; en webhook se registra el motivo y se
  omite la operación sin usar credenciales ajenas.
- RF-06: no incluir el token en mensajes de excepción, logs ni respuestas.
- RF-07: los clientes del SDK se reutilizan como instancias sin estado propio
  (la credencial viaja por operación).

### 3.3 Requisitos no funcionales

- RNF-01: sin dependencias nuevas ni cambios de esquema.
- RNF-02: sin sincronización global del SDK; la concurrencia queda aislada por
  construcción (cada llamada lleva su token).
- RNF-03: testeable con dobles de `OrderClient`/`PaymentClient` sin efectuar
  pagos reales.
- RNF-04: sin cambios de contrato en `IPasarelaPago`, `PagoService` ni los
  endpoints.

## 4. Casos de Uso

- Caso 1 (normal): negocio A inicia checkout → la Order se crea con el token
  de A (opciones por llamada).
- Caso 2 (concurrencia): checkout del negocio A y webhook del negocio B
  simultáneos → cada llamada al SDK lleva su token; ninguno se contamina.
- Caso 3 (error): negocio sin token → `PasarelaNoDisponibleException` (401/503
  según el flujo existente), sin llamada al SDK.
- Caso 4 (webhook): notificación con firma válida → se resuelve el negocio del
  pago y la consulta a MP usa su token; firma inválida sigue rechazándose.
- Caso 5 (límite): dos negocios con tokens distintos procesados en el mismo
  hilo de webhook en secuencia → cada consulta usa el token correcto (sin
  arrastre del anterior).

## 5. Criterios de Aceptación

- [ ] CA-01: dos negocios con credenciales distintas nunca intercambian
  tokens (verificado capturando `MPRequestOptions` en dobles).
- [ ] CA-02: solicitudes simultáneas permanecen aisladas (sin estado global;
  grep sin `setAccessToken`).
- [ ] CA-03: no se exponen credenciales en logs ni respuestas.
- [ ] CA-04: las pruebas usan dobles y no ejecutan pagos reales.
- [ ] CA-05: los webhooks continúan validando autenticidad (tests de firma
  existentes siguen pasando).
- [ ] CA-06: se preservan los contratos existentes de pagos y los tests
  existentes de `PagoService`/`StripeWebhookService` pasan.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- No cambiar la versión del SDK ni agregar dependencias.
- No modificar la lógica de firma HMAC de `WebhookService`.
- No tocar `ConfiguracionMercadoPago` salvo que fuera imprescindible
  (no lo es).

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión del usuario | Estado/fecha |
|----------|-------------------------|----------------------|--------------|
| D-01 Credencial por operación | (a) `MPRequestOptions.accessToken` por llamada (API verificada); (b) sincronizar `setAccessToken` + llamada (serializa y mantiene estado global); (c) una JVM por tenant (costo operativo) | (a) — recomendada e instrucción de implementar | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Alcance del webhook | (a) solo aislar credenciales (optimización de fan-out queda P1); (b) refactorizar el recorrido de negocios en la misma corrección | (a) — alcance mínimo; el fan-out se documenta como deuda P1 | Aprobada por instrucción del usuario, 2026-10-19 |
