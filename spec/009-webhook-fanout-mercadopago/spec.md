# SPEC — Acotar el fan-out de webhooks de Mercado Pago por negocio

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- `WebhookService.procesarPaymentMultiNegocio` recorre **todos** los negocios
  activos y llama a `paymentClient.get(paymentId, options)` con el token de cada
  uno hasta que alguno pueda leer el pago
  (`WebhookService.java:203-237`). Es O(negocios) llamadas externas por
  notificación de pago.
- Cuando el access token se resuelve desde una fuente global
  (`mercadopago.access-token` en system properties, entorno o archivo), **todos**
  los negocios resuelven el mismo token y el bucle repite la misma llamada N
  veces (`ConfiguracionMercadoPago.java:43-65`).
- `WebhookService.secretosFirmaCandidatos` recorre todos los negocios activos y
  resuelve un secreto por negocio (`WebhookService.java:140-155`). El
  `WebhookController` llama a `haySecretoFirmaConfigurado()` y a
  `validarFirma(...)` (`WebhookController.java:67-75`), por lo que un webhook de
  un pago/orden ya persistido hace hasta 2N lecturas de configuración.
- `PagoService.aplicarEstadoDePasarela` solo actualiza el pago cuando cambia el
  estado (`PagoService.java:96-99`). Si la primera notificación deja el mismo
  estado (por ejemplo, `PENDIENTE`), `mp_payment_id` no se persiste y las
  notificaciones posteriores no pueden usar un camino directo.
- `WebhookService.procesarOrder` recibe `paymentId = null` para notificaciones
  de tipo `order` (`WebhookService.java:167-169`) y no lo extrae de la Order,
  aunque el SDK expone `Order.getTransactions().getPayments()` y cada
  `OrderPayment.getId()` (verificado con `javap` sobre `sdk-java-3.7.0`).
- `MySqlRepositorioPago.actualizarWebhook` escribe `mp_payment_id = ?` y
  `order_id = ?` sin conservar el valor previo
  (`MySqlRepositorioPago.java:196-211`); una notificación de orden que cambie el
  estado y no traiga payment ID podría borrar el identificador persistido.
- `IPagoRepository.buscarPorMpPaymentId(String)` ya existe y devuelve el `Pago`
  con su `idNegocio` (`IPagoRepository.java:15`,
  `MySqlRepositorioPago.java:110-123`), por lo que el camino directo es viable
  sin cambios de esquema.

### 1.2 Discrepancias con el informe previo

El informe listaba el fan-out como deuda P1. La inspección confirma que el costo
no es solo de red: también hay lecturas de configuración repetidas por negocio
en la validación de firma, y que el fast-path ni siquiera puede activarse si la
primera notificación no cambia el estado del pago.

## 2. Objetivo

Acotar el trabajo de los webhooks de Mercado Pago al negocio dueño del
pago/orden cuando ya está persistido, eliminar llamadas externas duplicadas y
conservar los identificadores del pago, manteniendo intactos la validación de
firma, las validaciones de importe/moneda/referencia y el comportamiento para
pagos desconocidos en su primera notificación.

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: ninguna operación ni validación de firma puede usar credenciales de un
  negocio distinto al dueño del pago persistido.
- I-02: las validaciones de importe, moneda, referencia externa, Order ID y
  estado de la Order permanecen sin cambios.
- I-03: una notificación que no trae un identificador (payment ID u order ID)
  nunca borra el identificador ya persistido.
- I-04: una notificación de pago desconocida en su primera entrega se sigue
  resolviendo (fan-out acotado a tokens distintos) y una firma inválida se sigue
  rechazando con 401.

### 3.2 Requisitos funcionales

- RF-01: `WebhookService` intenta primero
  `pagoRepository.buscarPorMpPaymentId(paymentId)`; si encuentra un pago de
  Mercado Pago, usa exclusivamente el access token de
  `pago.getIdNegocio()` para `paymentClient.get` y el procesamiento posterior,
  sin recorrer negocios.
- RF-02: solo si el pago no está persistido se recorre la lista de negocios
  activos, resolviendo sus access tokens y **deduplicando por valor**. Se
  intenta una vez por token distinto y se corta en el primer éxito. Antes de
  procesar se verifica que el token usado coincida con el token resuelto para el
  negocio del pago encontrado por `order_id`.
- RF-03: `procesarOrder` extrae el payment ID de
  `Order.getTransactions().getPayments()` cuando la notificación no lo provee,
  prefiriendo un pago con estado `approved`/`processed` y, si no hay, el último
  ID no vacío. Ese ID se usa para persistir el pago.
- RF-04: `PagoService.aplicarEstadoDePasarela` actualiza el pago cuando cambia
  el estado **o** aparece un identificador nuevo no vacío. Si la notificación
  trae `null`/vacío para un identificador, pasa el valor existente en lugar de
  borrarlo. Actualiza también los campos del `Pago` en memoria.
- RF-05: la validación de firma resuelve el negocio por `order_id` o por
  `mp_payment_id` persistido y prueba solo el secreto global (si existe) y el
  del negocio resuelto. Si el ID no está persistido, conserva el
  comportamiento actual: prueba los secretos globales y de todos los negocios
  activos (HMAC local, sin llamadas externas).
- RF-06: sin cambios de esquema, configuración, versión del SDK ni endpoints.
  `WebhookController` pasa el tipo de notificación a las validaciones; los
  códigos de respuesta (200/400/401/429/503) no cambian.
- RF-07: no se registran tokens ni secretos en logs, excepciones ni respuestas.

### 3.3 Requisitos no funcionales

- RNF-01: el caso común (pago ya persistido) queda en O(1): una consulta local
  y como máximo una llamada a `paymentClient.get` y una a `orderClient.get`.
- RNF-02: con un access token global compartido, una notificación de pago
  desconocida hace una sola llamada a Mercado Pago aunque existan N negocios.
- RNF-03: sin dependencias nuevas ni cambios de esquema; testeable con dobles de
  `PaymentClient`/`OrderClient` y repositorios Mockito.
- RNF-04: la concurrencia se mantiene aislada por operación
  (`MPRequestOptions.accessToken`), sin estado global mutable.

## 4. Casos de Uso

- Caso 1 (fast-path): llega un webhook de pago cuyo ID ya está en
  `pagos.mp_payment_id` → se usa solo el token de ese negocio; una consulta a
  `paymentClient.get`, validación y procesamiento de la Order.
- Caso 2 (primera notificación de pago, token global): el pago no está
  persistido y todos los negocios comparten token → se hace **una** llamada
  externa, no N.
- Caso 3 (primera notificación de pago, tokens por negocio): se prueba cada
  token distinto una vez, en orden de negocio, hasta que uno lea el pago; se
  verifica que sea el token del negocio dueño antes de procesar.
- Caso 4 (notificación de orden): se resuelve el pago por `order_id`, se usa su
  token y, si la notificación no trae payment ID, se extrae de la Order y se
  persiste aunque el estado no cambie.
- Caso 5 (firma): una firma hecha con el secreto del negocio B no se acepta para
  una orden/pago del negocio A; para un ID desconocido se mantiene la
  validación contra los secretos configurados.
- Caso 6 (datos incompletos): una notificación de orden con estado ya igual al
  persistido y sin payment ID no borra `mp_payment_id`/`order_id`.
- Caso 7 (primera notificación de pago sin `order_id` local): la referencia
  externa contiene el ID de venta; se buscan los pagos de Mercado Pago de esa
  venta y solo se procesa si hay un único candidato del mismo negocio, sin
  contradicciones de `order_id`/`mp_payment_id`.
- Caso 8 (primera notificación de orden): se usa el token cuando todos los
  negocios activos comparten una única credencial distinta; con más de una
  credencial no se consulta Mercado Pago ni se modifica estado.

## 5. Criterios de Aceptación

- [x] CA-01: pago persistido → `paymentClient.get` se invoca una vez con el
  token del negocio dueño y no se recorre `listarActivos()`.
- [x] CA-02: pago desconocido con token global compartido y N negocios →
  `paymentClient.get` se invoca una vez.
- [x] CA-03: pago desconocido con tokens distintos → una invocación por token
  distinto, en orden, cortando en el primer éxito.
- [x] CA-04: orden sin payment ID en la notificación → se persiste el payment ID
  extraído de `Order.getTransactions().getPayments()`.
- [x] CA-05: `aplicarEstadoDePasarela` persiste un identificador nuevo aunque el
  estado no cambie, conserva los existentes ante `null`/vacío y no actualiza si
  no hay cambios.
- [x] CA-06: firma del negocio B rechazada para orden/pago del negocio A; firma
  de un ID desconocido sigue validándose con los secretos configurados.
- [x] CA-07: no se exponen tokens ni secretos en logs/respuestas.
- [x] CA-08: suite unitaria completa verde (sin `IdentidadGlobalMySqlTest`),
  sin llamadas reales a Mercado Pago.
- [x] CA-09: primera notificación de pago con `order_id` local nulo/vacío se
  resuelve por `external_reference` y persiste `order_id`/payment ID.
- [x] CA-10: primera notificación de orden en modo de token único resuelve el
  pago por `external_reference`, valida monto/moneda contra la API y persiste
  los identificadores.
- [x] CA-11: referencia inválida, venta inexistente, candidato de otro negocio,
  múltiples candidatos o identificadores contradictorios → warning y ningún
  cambio de estado.
- [x] CA-12: los identificadores ausentes en la notificación se conservan y un
  reintento idempotente no duplica la venta pagada ni el evento.
- [x] CA-13: la validación de importe y moneda usa la respuesta de Mercado Pago,
  nunca el body del webhook.
- [x] CA-14: con más de un token de negocio distinto, la primera notificación de
  orden no consulta Mercado Pago ni actualiza estado.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- No cambiar la versión del SDK ni agregar dependencias.
- No modificar el manifiesto HMAC ni los códigos de estado del endpoint.
- No tocar `ConfiguracionMercadoPago`. `IPagoRepository` y
  `MySqlRepositorioPago` solo pueden recibir el método de lectura de candidatos
  por venta aprobado en D-08; no se cambian escrituras ni el esquema.
- No ejecutar pagos reales ni pruebas destructivas.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión del usuario | Estado/fecha |
|----------|-------------------------|----------------------|--------------|
| D-01 Fast-path por pago persistido | (a) buscar por `mp_payment_id` y usar solo el token del dueño; (b) ignorar las notificaciones de tipo `payment` y depender solo de `order`; (c) agregar un mapeo persistente de IDs de pago | (a) — conserva el soporte actual de notificaciones de pago y no requiere esquema | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Fallback acotado para pago desconocido | (a) deduplicar tokens por valor y cortar al primer éxito; (b) eliminar el fan-out y rechazar pagos desconocidos; (c) mapear `user_id` de MP a negocio | (a) — preserva la primera entrega; (b) perdería confirmaciones si MP entrega solo el tópico `payment`; (c) requiere configuración/negocio nuevo | Aprobada por instrucción del usuario, 2026-10-19 |
| D-03 Alcance de la firma | (a) resolver el negocio por el ID persistido y probar solo su secreto (+ global); (b) dejar el recorrido por todos los negocios | (a) — reduce lecturas de configuración y aísla tenants; para IDs desconocidos se mantiene el fallback local | Aprobada por instrucción del usuario, 2026-10-19 |
| D-04 Conservación de identificadores | (a) preservar en `PagoService` los valores existentes cuando la notificación no los trae; (b) `COALESCE` en el SQL del repositorio | (a) — no toca SQL ni repositorios y es testeable en unidad | Aprobada por instrucción del usuario, 2026-10-19 |
| D-05 Payment ID desde la Order | (a) extraerlo de `Order.getTransactions().getPayments()`; (b) persistirlo solo cuando llega una notificación de tipo `payment` | (a) — habilita el fast-path aunque llegue primero la orden | Aprobada por instrucción del usuario, 2026-10-19 |
| D-06 Alcance de la optimización | (a) fast-path, deduplicación, firma acotada y conservación de IDs; (b) agregar caché negativa o mapeo `user_id`→negocio | (a) — alcance mínimo verificable; el fan-out residual de la primera entrega queda documentado | Aprobada por instrucción del usuario, 2026-10-19 |
| D-07 Fan-out de primera entrega | Eliminarlo requiere un identificador de tenant en la notificación (mapeo `user_id` de MP o URL de webhook por negocio) | Fuera de alcance; no se implementa sin decisión de negocio. D-08 mitiga la primera entrega en modo de token único | Diferida, 2026-10-19 |
| D-08 Correlación de primera entrega | (a) usar el ID global de venta ya enviado como `external_reference` y buscar candidatos MP de esa venta con el tenant validado en el JOIN; (b) mapear `user_id`/`collector_id` de MP a negocio; (c) URL de webhook por negocio | (a) — resuelve la primera notificación sin esquema ni multi-cuenta; la primera Order exige una única credencial distinta y se omite si hay varias | Aprobada por instrucción del usuario, 2026-10-20 |
