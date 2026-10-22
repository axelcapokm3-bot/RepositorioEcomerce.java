# PLAN — Erradicación de XSS en el frontend (carrito y notificaciones)

## 1. Alcance e Impacto

- **Especificación:** `spec/006-xss-frontend/spec.md` y este plan.
- **Impacto:** `HIGH` — ejecución de código en el navegador de usuarios
  (robos de sesión/localStorage, acciones en nombre del usuario).
- **Dentro de alcance:** `renderCart` por nodos DOM, toast con mensaje por
  `textContent`, auditoría de `sales-list.js` y `negocio-switcher.js`.
- **Fuera de alcance:** backend, CSP/headers (P1), SRI (P1), reescritura
  general del frontend, infraestructura de tests JS permanente.

## 2. Arquitectura y Componentes

- **`src/js/components/cart-sidebar.js`:**
  - `renderCart`: reemplazar la plantilla `innerHTML` por creación de nodos:
    contenedor `.cart-item`, `span` de nombre con `textContent`, `span` de
    subtotal, botón `remove-from-cart-btn` con `dataset.productId` y listener
    directo. Vaciar el contenedor con `replaceChildren()`/`innerHTML = ''` y
    luego adjuntar nodos.
  - `checkout` (spec 007) se implementa sobre esta base.
- **`src/js/utils/toast-notifier.js`:**
  - Mantener `innerHTML` solo con el theme estático; añadir
    `<div data-rol="mensaje">` y asignar `textContent = String(message)`.
- **Auditoría:** `src/js/components/sales-list.js` (:36) y
  `src/js/components/negocio-switcher.js` (:101); documentar resultado.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| `escaparHtml` sobre las plantillas actuales | Depende de disciplina futura; un olvido reintroduce el XSS; escapar HTML a mano tiene bordes (atributos, comillas) |
| DOMPurify u otra librería | Dependencia nueva y sobredimensionada para dos puntos concretos; sin `package.json` en el repo |
| Sanitizar en backend | No protege contra datos ya almacenados; mezcla responsabilidades y no cubre mensajes de error locales |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/js/components/cart-sidebar.js` (también spec 007)
- `src/js/utils/toast-notifier.js`

### Archivos a auditar (editar solo si se demuestra interpolación sin escapar)

- `src/js/components/sales-list.js`
- `src/js/components/negocio-switcher.js`

### Dependencias con otras especificaciones

- `spec/007-checkout-url-pago`: comparte `cart-sidebar.js`; 006 se implementa
  primero y 007 se apoya en los nodos ya saneados.

## 4. Estrategia y Orden de Implementación

1. `toast-notifier.js`: mensaje por `textContent` (RF-02).
2. `cart-sidebar.js`: `renderCart` por nodos (RF-01).
3. Auditoría de los dos archivos restantes (RF-04).
4. Verificación:
   - `node --check` sobre los archivos modificados.
   - Prueba de humo con `node` y dobles de DOM: cargar los módulos, simular
     `renderCart`/`showToast` con payload XSS y verificar que el valor queda
     como texto y que no se crean nodos `script`/`img`.

### Riesgos de regresión

- El CSS del carrito depende de `span` dentro de `.cart-item`; se conserva la
  misma estructura (dos `span`, botón dentro del segundo).
- `removeFromCart` parsea `dataset.productId` con `parseInt`; se mantiene el
  mismo formato numérico.

### Estrategia de pruebas

- **Humo con `node` (obligatoria):** stubs de `document`/`sessionStorage` que
  registren nodos; payloads `<img src=x onerror=alert(1)>` y
  `<script>alert(1)</script>`; asserts de `textContent` y ausencia de HTML.
- **Manual/visual (opcional):** abrir el catálogo con un producto de prueba y
  verificar apariencia de carrito y toasts.
- **Regresión:** `node --check` y, si existen, tests backend no afectados.

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (implementación
  solicitada el 2026-10-19; se adoptan las opciones recomendadas).
- **Decisiones bloqueantes:** D-01 y D-02 de `spec.md` §7 — resueltas.
- **Decisión del usuario y fecha:** "implementa cuando lo tengas armado",
  2026-10-19.
