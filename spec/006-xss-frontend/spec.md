# SPEC — Erradicación de XSS en el frontend (carrito y notificaciones)

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- `cart-sidebar.js:33-40`: `renderCart` construye HTML por concatenación e
  inyecta `item.nombre` y el subtotal con `innerHTML`:
  ```js
  cartItemsContainer.innerHTML = cart.map(item => `
      <div class="cart-item">
          <span>${item.nombre} x ${item.quantity}</span>
          ...
  `).join('');
  ```
  `item.nombre` proviene de los productos servidos por la API
  (`addToCart(product)` en `:7-16` y `app.js` al agregar desde el catálogo),
  es decir, de datos almacenados, no de literales del código. Un nombre como
  `<img src=x onerror=alert(1)>` ejecuta script en el navegador de cualquier
  usuario con el carrito abierto.
- `toast-notifier.js:38-50`: `toast.innerHTML` interpola `${message}` (:45).
  Varias llamadas pasan mensajes derivados de datos/errores externos:
  `showToast(`${product.nombre} añadido al carrito`)`
  (`cart-sidebar.js:14`) y mensajes de error propagados desde la API.
- `app.js:888-892` ya define `escaparHtml` como precedente interno de
  codificación, pero los módulos afectados no lo usan.
- `toast-notifier.js` y `cart-sidebar.js` no tienen tests; no hay
  infraestructura de tests JS en el repo (no `package.json`).

### 1.2 Discrepancias con el informe previo

El informe señalaba `cart-sidebar.js` como el punto XSS principal. Se confirma
y se agrega `toast-notifier.js` como segundo vector (el informe no lo
mencionaba): cualquier mensaje que interpole datos externos por `innerHTML` es
explotable, y el carrito llama a `showToast` con el nombre del producto.

`sales-list.js:36` usa plantilla HTML, pero sus datos dinámicos se limitan a
identificadores numéricos, fechas y valores de enums del backend
(`estadoCompra`, `tipoPago`), sin texto libre de usuario, y
`negocio-switcher.js:101` usa contenido estático; se inspeccionan como parte
del alcance y se documenta que no requieren cambio (auditoría 2026-10-19:
ninguno interpola texto libre).

## 2. Objetivo

Eliminar la ejecución de HTML/JS inyectado en el carrito y en las
notificaciones, construyendo nodos con `textContent` para todo dato dinámico,
sin perder apariencia, clases ni comportamiento de los componentes.

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: ningún dato dinámico (nombre de producto, mensaje de error, texto de
  usuario) se interpreta como HTML en el DOM.
- I-02: la apariencia, clases CSS, selectores (`cart-item`,
  `remove-from-cart-btn`, `[data-product-id]`, contenedor de toasts) y flujos
  permanecen idénticos.
- I-03: no se rompe la eliminación de ítems ni el cierre de toasts.

### 3.2 Requisitos funcionales

- RF-01: `renderCart` construye cada fila con `document.createElement` y
  asigna nombre/subtotal con `textContent`; el botón de quitar conserva clase
  `remove-from-cart-btn` y `dataset.productId`; el total sigue con
  `textContent`.
- RF-02: el markup estático del toast (iconos, títulos de tema, clases) puede
  seguir usando `innerHTML`, pero el mensaje dinámico se inserta en un nodo
  identificado (p. ej. `data-rol="mensaje"`) con `textContent`.
- RF-03: los tipos de toast (`error`, `success`, `info`) y la animación/cierre
  se preservan.
- RF-04: auditar `sales-list.js` y `negocio-switcher.js`: si escapan o usan
  contenido estático, documentar "sin cambio" en la verificación; si aparece
  interpolación sin escapar, corregir en el mismo estilo.
- RF-05: no duplicar escape (no aplicar `escaparHtml` y `textContent` a la
  vez); con `textContent` nunca se interpreta HTML.
- RF-06: payloads de prueba con `<img src=x onerror=...>`, `<script>`,
  comillas y `&` deben mostrarse como texto visible.

### 3.3 Requisitos no funcionales

- RNF-01: sin dependencias nuevas ni cambios de API pública de los módulos.
- RNF-02: compatible con el navegador objetivo del proyecto (DOM estándar,
  `dataset`, `textContent`).
- RNF-03: verificable con `node --check` y pruebas de humo con dobles de DOM
  sin framework (coherente con el estado actual del repo).

## 4. Casos de Uso

- Caso 1: producto llamado `<img src=x onerror=alert(1)>` agregado al carrito
  → se muestra el texto literal, sin ejecución.
- Caso 2: error de API cuyo mensaje contiene `<script>alert(1)</script>` →
  el toast muestra el texto, sin ejecución.
- Caso 3: producto nombre con `<b>Oferta</b> & Cía.` → se ve el texto tal cual
  (sin negrita interpretada).
- Caso 4: carrito con varios ítems → cada botón X sigue quitando su producto.
- Caso 5: toasts `success`, `error`, `info` → mismos colores, iconos y
  auto-cierre.

## 5. Criterios de Aceptación

- [ ] CA-01: con payload XSS en el nombre, el nodo del carrito lo muestra como
  texto (no contiene elementos `img`/`script`).
- [ ] CA-02: con payload XSS en el mensaje, el toast lo muestra como texto.
- [ ] CA-03: las clases y selectores usados por el CSS y por los listeners
  siguen intactos; quitar del carrito funciona.
- [ ] CA-04: `node --check` pasa en los archivos modificados.
- [ ] CA-05: no se introducen dependencias ni se toca el backend.
- [ ] CA-06: auditoría de `sales-list.js`/`negocio-switcher.js` documentada
  (sin cambio justificado o corregido).

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- No cambiar UX ni estilos; los toasts mantienen su theme y animación.
- No crear infraestructura de tests JS nueva si no es imprescindible; usar
  verificaciones con `node` (stubs) y `node --check`.
- No tocar backend ni `api.js` salvo necesidad demostrada (no lo es).

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión del usuario | Estado/fecha |
|----------|-------------------------|----------------------|--------------|
| D-01 Técnica de saneamiento | (a) `textContent` con nodos DOM (a prueba de fallos, sin escapado manual); (b) `escaparHtml` sobre plantillas; (c) librería externa de sanitización (dependencia nueva) | (a) — recomendada e instrucción de implementar | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Alcance de archivos | (a) corregir carrito y toast (vectores confirmados) + auditar el resto; (b) reescribir todo el frontend | (a) — alcance mínimo | Aprobada por instrucción del usuario, 2026-10-19 |
