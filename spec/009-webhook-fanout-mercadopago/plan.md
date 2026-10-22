# PLAN — Acotar el fan-out de webhooks de Mercado Pago por negocio

## 1. Alcance e Impacto

- **Especificación:** `spec/009-webhook-fanout-mercadopago/spec.md` y este plan.
- **Impacto:** `MEDIUM` — afecta el procesamiento asíncrono de notificaciones de
  pago y la validación de firma del webhook; no cambia contratos HTTP.
- **Dentro de alcance:** fast-path por `mp_payment_id`, deduplicación de tokens,
  extracción del payment ID desde la Order, conservación de identificadores,
  validación de firma acotada al negocio y tests deterministas.
- **Fuera de alcance:** eliminar el fan-out de primera entrega (mapeo
  `user_id`→negocio o webhook por negocio), caché negativa, cambios de esquema,
  cambios al manifiesto HMAC, Stripe y frontend.

## 2. Arquitectura y Componentes

- **CapaAplicacion — `WebhookService`:**
  - `procesarPaymentMultiNegocio` se divide en:
    - `procesarPagoPersistido(paymentId)`: busca por `mp_payment_id`, filtra
      pagos de Mercado Pago y usa solo el token de `pago.getIdNegocio()`.
    - Fallback: `tokensAccesoDistintos()` resuelve los tokens de los negocios
      activos en un `LinkedHashSet` (orden de negocio, sin duplicados) y prueba
      uno por uno hasta el primer pago legible.
    - `procesarPagoVerificado(...)`: comparte la validación
      (`validarPago`, coincidencia de Order ID y `procesarOrder`) entre el
      fast-path y el fallback; exige que el token usado sea el del negocio del
      pago.
  - `procesarOrder` extrae el payment ID de la Order cuando el argumento es
    nulo/vacío (`Order.getTransactions().getPayments()`), prefiere
    `approved`/`processed` y cae al último ID no vacío.
  - `validarFirma` y `haySecretoFirmaConfigurado` ganan una sobrecarga con
    `(tipo, id)`:
    - orden → `buscarPorOrderId`;
    - pago → `buscarPorMpPaymentId` (solo pagos de Mercado Pago);
    - ID persistido → candidatos = secreto global + secreto del negocio;
    - ID desconocido → candidatos globales + de todos los negocios activos
      (comportamiento actual).
  - La variante sin tipo delega en la variante con tipo para no romper
    `WebhookServiceTest`.
- **CapaAplicacion — `PagoService.aplicarEstadoDePasarela`:**
  - Calcula `mpPaymentIdPersistido` y `orderIdPersistido` conservando el valor
    actual si el nuevo es `null`/vacío.
  - Actualiza si cambió el estado o si apareció un identificador nuevo; no
    actualiza si no hay cambios.
  - Refleja los identificadores conservados/nuevos en el objeto `Pago`.
- **CapaPresentacion — `WebhookController`:** pasa `notificationType` a
  `haySecretoFirmaConfigurado` y `validarFirma`; mismos códigos de respuesta.
- **CapaInfraestructura:** sin cambios (`IPagoRepository`,
  `MySqlRepositorioPago`, `ConfiguracionMercadoPago` intactos).
- **CapaDominio:** sin cambios.
- **Frontend:** sin cambios.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| Ignorar notificaciones `payment` y depender solo de `order` | Pierde confirmaciones si MP entrega solo el tópico de pagos |
| Mapear `user_id` de MP a negocio | Requiere configuración/negocio nuevo y no está en el alcance aprobado |
| Persistir un mapeo `paymentId`→`idNegocio` desde el checkout | El payment ID no existe al crear la Order |
| `COALESCE` en el SQL de `actualizarWebhook` | Toca infraestructura y no es necesario si el servicio conserva los valores |
| Caché negativa de payment IDs | Complejidad y expiración no justificadas para este alcance |
| Cambiar el manifiesto HMAC | No es parte del problema y rompería compatibilidad con MP |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/com/CapaAplicacion/Service/WebhookService.java`
- `src/com/CapaAplicacion/Service/PagoService.java`
- `src/com/CapaPresentacion/Controllers/WebhookController.java`
- `test/java/com/CapaAplicacion/Service/PagoServiceTest.java` (casos de
  conservación/actualización de identificadores).
- `test/java/com/CapaAplicacion/Service/WebhookServiceCredencialesTest.java`
  (mantener el caso existente; ajustar si cambia la firma usada).

### Archivos nuevos

- `test/java/com/CapaAplicacion/Service/WebhookServiceFanoutTest.java`:
  fast-path, deduplicación de tokens, orden de intentos, extracción de payment
  ID desde la Order y validación de firma acotada. Sin llamadas reales a MP.

### Dependencias con otras especificaciones

- `spec/001-orders-api-checkout-pro`: la Order es la fuente de verdad; el
  payment ID es auxiliar y ahora se persiste también desde la Order.
- `spec/005-credenciales-mercadopago`: se reutiliza `MPRequestOptions` por
  operación; no se muta estado global del SDK.
- `spec/007-checkout-url-pago`: el estado final sigue proviniendo del webhook;
  no se modifica el flujo de retorno.
- `spec/008-cancelacion-venta-atomica`: independiente.

## 4. Estrategia y Orden de Implementación

1. `PagoService.aplicarEstadoDePasarela`: conservación de identificadores y
   actualización por cambio de estado o de identificador.
2. `WebhookService`: fast-path por `mp_payment_id`, deduplicación de tokens,
   helper de procesamiento verificado y extracción de payment ID desde la Order.
3. `WebhookService`: sobrecargas de `validarFirma`/`haySecretoFirmaConfigurado`
   con tipo e ID, y candidatos de secreto acotados.
4. `WebhookController`: pasar el tipo de notificación.
5. Tests: `PagoServiceTest` + `WebhookServiceFanoutTest`; ajustar
   `WebhookServiceCredencialesTest` si corresponde.
6. Verificación: `mvn -q test-compile`, tests dirigidos y suite unitaria sin
   `IdentidadGlobalMySqlTest`.

### Riesgos de regresión

- El fast-path usa el `idNegocio` del pago persistido; si una fila tuviera un
  negocio incorrecto, se usaría su token y la validación de Order/monto lo
  rechazaría. No se relaja ninguna validación.
- El fallback deduplica por valor de token: dos negocios con el mismo token
  comparten credencial (caso global); la verificación contra el token del
  negocio del pago evita procesar con credenciales ajenas.
- La validación de firma acotada podría rechazar una firma válida si el pago
  persistido apunta a un negocio con secreto distinto al que firmó. Ese caso es
  inconsistencia de configuración; se documenta y se registra sin exponer
  secretos.
- Persistir identificadores en estados sin cambio agrega un `UPDATE` adicional
  solo cuando aparece un identificador nuevo; no altera eventos ni estados.
- Los tests existentes de firma sin tipo siguen usando el camino completo.

### Estrategia de pruebas

- **Mockito:** repositorios y `PaymentClient`/`OrderClient`; se capturan
  `MPRequestOptions` para verificar el token por operación.
- **Fast-path:** pago persistido → una llamada a `paymentClient.get`, sin
  `listarActivos()`.
- **Deduplicación:** N negocios con el mismo token → una llamada; tokens
  distintos → una por token y corte en el primer éxito.
- **Order → payment ID:** Order `pending` con `transactions.payments` →
  `actualizarWebhook` recibe el ID extraído aunque el estado no cambie.
- **PagoService:** identificador nuevo con estado igual actualiza; `null`
  conserva; sin cambios no actualiza.
- **Firma:** secreto del negocio B rechazado para una orden del negocio A; ID
  desconocido valida con cualquier secreto configurado.
- **Regresión:** `WebhookServiceTest`, `WebhookControllerTest`,
  `WebhookServiceCredencialesTest`, `StripeWebhookServiceTest`,
  `PagoServiceTest` y suite completa sin `IdentidadGlobalMySqlTest`.

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (dirección elegida
  explícitamente en la sesión del 2026-10-19; se adoptan las opciones
  recomendadas).
- **Decisiones bloqueantes:** D-01 a D-06 resueltas; D-07 queda fuera de
  alcance; D-08 aprueba la mitigación de primera entrega.
- **Decisión del usuario y fecha:** "siguiente" tras completar spec 008
  (P1 fan-out de webhooks de Mercado Pago), 2026-10-19.

## 6. Actualización D-08 — Primera entrega (2026-10-20)

- **Alcance:** resolver la primera notificación de Mercado Pago cuando el pago
  local todavía no tiene `order_id`, usando `external_reference` con el ID
  global de venta. No se agrega mapeo `user_id`, no se cambia el esquema y no se
  modifica el contrato HTTP.
- **Repositorio:** `IPagoRepository.buscarCandidatosMercadoPagoPorVenta(int)`
  devuelve los pagos MP de una venta; `MySqlRepositorioPago` valida el tenant
  con `JOIN ventas v ON v.id = p.id_venta AND v.id_negocio = p.id_negocio`.
  Es un método de lectura; no cambia escrituras.
- **WebhookService:**
  - Pago sin `order_id`: tras el fast-path por `mp_payment_id`, resuelve la
    venta desde `Payment.getExternalReference()` y exige exactamente un
    candidato MP del mismo negocio, sin contradicción de `order_id` ni
    `mp_payment_id`.
  - Order sin `order_id`: si todos los negocios activos comparten una única
    credencial distinta, consulta la Order con ese token y resuelve el pago por
    `external_reference`; con más de un token no consulta ni actualiza.
  - `validarOrder` acepta `order_id` local nulo/vacío; si existe, exige
    coincidencia. Monto y moneda se validan contra la respuesta de la API.
- **Idempotencia:** se conservan los identificadores existentes ante
  `null`/vacío y los reintentos no duplican la venta pagada ni el evento.
- **Archivos:** `WebhookService.java`, `IPagoRepository.java`,
  `MySqlRepositorioPago.java`, `WebhookServicePrimeraEntregaTest.java`,
  `MySqlRepositorioPagoTest.java` y
  `PasarelaMercadoPagoCredencialesTest.java`.
- **Pruebas:** primera Payment/Order sin `order_id`, referencia inválida, venta
  inexistente, tenant ajeno, múltiples candidatos, importe/moneda incorrectos,
  `order_id` contradictorio, reintento idempotente, IDs nulos/vacíos y tokens
  múltiples. Sin llamadas reales a Mercado Pago.
- **Riesgo residual:** la primera Order con más de un token distinto se omite
  con warning; el multi-cuenta sigue fuera de alcance (D-07).
