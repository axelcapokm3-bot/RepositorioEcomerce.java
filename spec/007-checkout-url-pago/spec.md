# SPEC — Checkout con redirección segura a la pasarela y recuperación del pago

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- `cart-sidebar.js:95-100`: tras `await api.ventas.crear(...)` el código
  asume éxito sin inspeccionar el resultado:
  ```js
  await api.ventas.crear(createVentaRequest, claveIdempotencia);
  showToast('Compra finalizada con éxito!', 'success');
  cart.length = 0; // Clear cart
  ```
  La venta queda `PENDIENTE` y el usuario nunca es redirigido a la pasarela
  (`urlPago` del `ResultadoPago` se ignora). El usuario cree que pagó.
- `app.js:1018-1023` (POS) sí muestra `resultado.urlPago` como enlace, lo que
  confirma que el dato viaja en la respuesta y que el carrito es el que lo
  descarta.
- `cart-sidebar.js:101-104`: ante cualquier error se muestra un mensaje
  genérico y se pierde la clave de idempotencia generada (:91-93); un reintento
  del usuario crea una **nueva** venta y vuelve a descontar stock.
- `cart-sidebar.js:98`: el carrito se vacía incluso cuando el resultado no es
  `APROBADO`; si el pago quedó pendiente o falló, el usuario pierde la
  referencia de lo comprado y no tiene forma de retomar el pago (el backend sí
  expone `POST /ventas/{id}/pago`, `api.js:185`).
- `ResultadoPago` (record) devuelve `estado` (`EstadoPago`), `urlPago`,
  `transaccionId` (= id de venta), `mensaje`, `paymentId`. El flujo debe
  distinguir `APROBADO` / `PENDIENTE` / fallo, no asumir éxito.
- El propio `PagoService`/`WebhookService` documentan que la confirmación real
  llega por webhook; la redirección del navegador **no** confirma el pago.

### 1.2 Discrepancias con el informe previo

El informe indicaba "checkout no redirige a `urlPago`". Se confirma y además
se detectan dos defectos asociados no reportados: (a) el carrito se limpia sin
pago aprobado y (b) la clave de idempotencia se pierde en cada reintento,
generando ventas duplicadas. La URL de retorno podría venir manipulada por un
servidor comprometido o mal configuración, por lo que la redirección requiere
validación explícita (https + host permitido).

## 2. Objetivo

Que finalizar la compra en el carrito: (1) valide y redirija al `urlPago`
seguro devuelto por la pasarela cuando el estado sea `PENDIENTE`; (2) refleje
el estado real (`APROBADO`, `PENDIENTE`, rechazo); (3) conserve el carrito y la
referencia pendiente para reintentar sin duplicar la venta; y (4) nunca afirme
que el pago se completó cuando solo se inició.

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: solo se limpia el carrito cuando `estado === 'APROBADO'`.
- I-02: la clave de idempotencia se genera antes de la primera llamada y se
  reutiliza en los reintentos de la misma compra, hasta resolverse.
- I-03: solo se redirige a URLs `https` cuyo host pertenezca a la lista de
  hosts de pasarelas permitidas (`checkout.stripe.com` y dominios
  `mercadopago.*` configurados).
- I-04: la redirección (o el retorno a `success_url`) no marca la venta como
  pagada; la confirmación proviene del webhook/estado del backend.

### 3.2 Requisitos funcionales

- RF-01: al confirmar el carrito, generar clave de idempotencia y persistir un
  pendiente en `sessionStorage` (`ecommerce.checkout.pendiente`) con
  `{ claveIdempotencia, idCliente, idVenta?, creadoEn }` antes de llamar a la
  API.
- RF-02: inspeccionar el resultado de `api.ventas.crear`:
  - `estado === 'APROBADO'` → toast de éxito, limpiar pendiente, vaciar
    carrito y cerrar el panel.
  - `estado === 'PENDIENTE'` + `urlPago` válida → guardar `idVenta`
    (`transaccionId`) en el pendiente y redirigir con
    `window.location.assign(urlPago)`.
  - `estado === 'PENDIENTE'` sin `urlPago` válida → conservar carrito y
    pendiente, informar que el pedido quedó pendiente y que puede retomarse.
  - Rechazado/cancelado → conservar carrito y pendiente, mostrar el mensaje
    del backend (`resultado.mensaje`) o uno genérico, permitir reintentar.
- RF-03: ante error de red/HTTP conservar carrito y pendiente; toast con el
  mensaje del error si está disponible; nunca limpiar el carrito.
- RF-04: si existe un pendiente con `idVenta` y el usuario vuelve a confirmar
  (o al cargar la página), ofrecer "Retomar pago" que llama a
  `api.ventas.pagar(idVenta)` y aplica RF-02 sin volver a crear la venta.
- RF-05: si el usuario reintenta sin `idVenta` (fallo antes de crear), reusar
  la misma `claveIdempotencia` del pendiente; el backend devolverá la venta ya
  creada (idempotencia) en lugar de duplicar.
- RF-06: validar `urlPago`: parsear con `URL`; exigir `https:`; host exacto o
  subdominio de la allowlist; cualquier otro caso → tratar como "sin URL".
- RF-07: no tocar el flujo POS de `app.js` (ya muestra el enlace); solo el
  carrito.
- RF-08: no afirmar "pago confirmado" al redirigir; el mensaje es "pedido
  creado / pago pendiente", y el estado final se conoce por webhook.

### 3.3 Requisitos no funcionales

- RNF-01: sin dependencias nuevas; helpers internos con `try/catch` para
  `sessionStorage` (modo privado/incógnito no debe romper el checkout).
- RNF-02: no romper la API pública de `cart-sidebar.js`
  (`addToCart`, `removeFromCart`, `renderCart`, `toggleCart`, `checkout`).
- RNF-03: verificable con pruebas de humo en `node` con dobles de DOM,
  `fetch`, `sessionStorage` y `window.location`; sin pagos reales.
- RNF-04: no registrar tokens, claves ni datos personales en consola.

## 4. Casos de Uso

- Caso 1: pago pendiente con URL válida → redirección segura y pendiente
  guardado.
- Caso 2: pago pendiente sin URL → se conserva todo y se explica cómo
  reintentar.
- Caso 3: URL manipulada (`javascript:`, `http:`, host ajeno) → no se
  redirige; se trata como sin URL.
- Caso 4: reintento tras error de red → misma clave, sin stock duplicado.
- Caso 5: usuario vuelve a la tienda con pendiente guardado → botón "Retomar
  pago" que usa `POST /ventas/{id}/pago`.
- Caso 6: resultado `APROBADO` (p. ej. webhook ya aplicado y la pasarela
  responde aprobado) → recién aquí se vacía el carrito.
- Caso 7: rechazo → se conserva el carrito y se muestra el mensaje.

## 5. Criterios de Aceptación

- [ ] CA-01: con `estado=PENDIENTE` y URL permitida, se llama a
  `window.location.assign` con exactamente esa URL.
- [ ] CA-02: con URL `javascript:`, `http:` o host no permitido, no hay
  redirección y el carrito queda intacto.
- [ ] CA-03: el carrito solo se vacía con `APROBADO`.
- [ ] CA-04: ante error, la clave de idempotencia se reutiliza en el
  reintento (una sola venta para la misma compra).
- [ ] CA-05: el pendiente sobrevive a recargas de la página y habilita el
  retomar pago.
- [ ] CA-06: ningún mensaje afirma que el pago se completó cuando está
  pendiente.
- [ ] CA-07: `node --check` y las pruebas de humo pasan; sin cambios de
  backend.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- No cambiar `api.js` (ya expone `crear` con clave y `pagar`).
- No modificar el backend ni los contratos de `ResultadoPago`.
- No ejecutar pagos reales ni llamadas a las pasarelas en las pruebas.
- Comparte archivo con `spec/006-xss-frontend`; 007 se implementa después.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión del usuario | Estado/fecha |
|----------|-------------------------|----------------------|--------------|
| D-01 Redirección | (a) `window.location.assign` solo con allowlist https; (b) abrir en pestaña nueva (popup blockers); (c) renderizar enlace para que el usuario haga clic | (a) — recomendada; mantiene la continuidad del pago | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Idempotencia del reintento | (a) persistir pendiente con la clave generada y reusarla; (b) generar clave nueva por intento (duplica ventas) | (a) — recomendada | Aprobada por instrucción del usuario, 2026-10-19 |
| D-03 Limpieza del carrito | (a) solo al `APROBADO`; (b) limpiar al crear el pedido (comportamiento actual) | (a) — recomendada | Aprobada por instrucción del usuario, 2026-10-19 |
| D-04 Recuperación | (a) botón "Retomar pago" con `POST /ventas/{id}/pago`; (b) dejar el pendiente solo informativo | (a) — recomendada | Aprobada por instrucción del usuario, 2026-10-19 |
