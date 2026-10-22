# PLAN — Checkout con redirección segura a la pasarela y recuperación del pago

## 1. Alcance e Impacto

- **Especificación:** `spec/007-checkout-url-pago/spec.md` y este plan.
- **Impacto:** `HIGH` — UX de cobro y dinero: hoy se declara "compra
  finalizada" sin pago y se puede duplicar la venta al reintentar.
- **Dentro de alcance:** `checkout()` del carrito, pendiente en
  `sessionStorage`, validación/redirect de `urlPago`, recuperación con
  `api.ventas.pagar`, mensajes por estado.
- **Fuera de alcance:** backend, `api.js`, POS (`app.js`), Stripe/MP del lado
  servidor, persistencia del pendiente más allá de la sesión del navegador,
  polling de estado.

## 2. Arquitectura y Componentes

- **`src/js/components/cart-sidebar.js`:**
  - Constantes `CLAVE_PENDIENTE = 'ecommerce.checkout.pendiente'` y
    `HOSTS_PAGO_PERMITIDOS` (Stripe + dominios de Mercado Pago).
  - Helpers: `leerPendiente()`, `guardarPendiente(datos)`,
    `limpiarPendiente()`, `crearClaveIdempotencia()`, `urlPagoPermitida(url)`
    (exportado para pruebas).
  - `checkout()`: valida carrito/tipo de pago/cliente como hoy; genera o
    reutiliza clave; persiste pendiente; decide entre `api.ventas.pagar`
    (si hay `idVenta` pendiente) o `api.ventas.crear`; aplica el estado del
    resultado con `aplicarResultadoCheckout(resultado)`.
  - `aplicarResultadoCheckout`: `APROBADO` → limpiar; `PENDIENTE` con URL
    válida → guardar `idVenta` + `location.assign`; `PENDIENTE` sin URL o
    fallo → conservar y avisar.
  - `retomarPagoPendiente()` + botón flotante "Retomar pago" en
    `DOMContentLoaded` cuando hay pendiente con `idVenta`.
- **`api.js`:** sin cambios (`ventas.crear(payload, clave)` en :178-182,
  `ventas.pagar(id)` en :185).
- **Backend (`VentaController`/`VentaService`):** sin cambios; la idempotencia
  ya existe (`buscarPorClaveIdempotenciaSolicitud`, `spec/004` documenta el
  momento en que se descuenta stock).
- **Spec 006:** comparte archivo; 007 se implementa sobre `renderCart` ya
  saneado.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| `window.open(urlPago)` en pestaña nueva | Bloqueada por popup blockers fuera de gesto de usuario; rompe la continuidad del pago |
| Mostrar un enlace y que el usuario haga clic | Peor UX para el flujo natural del carrito; el POS ya conserva ese comportamiento y se mantiene |
| Guardar el pendiente en `localStorage` | El pendiente es por pestaña/sesión; `localStorage` filtraría estado entre pestañas y usuarios del mismo equipo |
| Reintentar generando clave nueva | Duplica la venta y el descuento de stock (el defecto actual) |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/js/components/cart-sidebar.js` (incluye cambios de 006 en
  `renderCart`).

### Archivos nuevos

- Ninguno de producción. Verificación de humo con script temporal fuera del
  repo (`node` con dobles) o inline en la shell.

### Dependencias con otras especificaciones

- `spec/006-xss-frontend`: comparte archivo; orden 006 → 007.
- `spec/004-stock-concurrencia`: la venta/descuento ocurre al crear el
  checkout; 007 no cambia ese momento ni reintenta la creación con otra clave.
- `spec/005-credenciales-mercadopago`: produce `urlPago` por negocio; 007 solo
  valida y usa la URL.

## 4. Estrategia y Orden de Implementación

1. Helpers de pendiente + allowlist + clave (RF-01, RF-06).
2. `checkout()` con decisión por estado y redirección (RF-02, RF-04, RF-05).
3. Recuperación "Retomar pago" (RF-04).
4. Mensajes sin afirmar pago confirmado (RF-08) y conservación del carrito
   (RF-03).
5. Verificación con `node` (dobles) para CA-01..CA-07 y `node --check`.

### Riesgos de regresión

- Los toasts dependen del contenedor `#toast-container`; la prueba de humo
  debe tratar la ausencia del contenedor como no-op (ya lo hace
  `showToast`).
- `sessionStorage` puede fallar en modo restringido: helpers con `try/catch`.
- El botón "Retomar pago" no debe duplicarse en rerenders: se crea solo si no
  existe.

### Estrategia de pruebas

- **Humo `node` (obligatoria):** dobles de `document`, `sessionStorage`,
  `window.location`, `crypto.randomUUID` y `fetch`; escenarios CA-01..CA-06;
  asserts de llamadas a redirección y de conservación/limpieza del carrito.
- **Manual (opcional):** carrito con un producto y tipo de pago; observar
  redirección con backend local configurado; sin pagos reales.
- **Regresión:** `node --check` en los archivos tocados.

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (implementación
  solicitada el 2026-10-19; se adoptan las opciones recomendadas).
- **Decisiones bloqueantes:** D-01 a D-04 de `spec.md` §7 — resueltas.
- **Decisión del usuario y fecha:** "implementa cuando lo tengas armado",
  2026-10-19.
