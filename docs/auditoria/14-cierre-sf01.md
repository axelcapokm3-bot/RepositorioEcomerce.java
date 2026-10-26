# SF-01 — Cabeceras de seguridad (backend)

## Alcance y decisión

Se implementaron las cabeceras de seguridad en el backend. El usuario aprobó el
alcance **solo backend**: no se modificó el frontend ni se migraron sus CDNs,
scripts inline o estilos inline en este bloque.

El servidor HTTP solo registra contextos `/api/*` (`ServerMain.java:181-194`).
El HTML y los módulos JS se sirven con Live Server/Caddy, por lo que las
cabeceras de este cambio aplican a las respuestas del backend y **no** a los
documentos `index.html` ni `login.html`.

## Cambios implementados

- `src/com/CapaPresentacion/Utilidades/CabecerasSeguridad.java`: helper único
  `CabecerasSeguridad.aplicar(HttpExchange)`.
- `src/com/CapaPresentacion/Controllers/BaseController.java`: el helper se
  aplica en `handlePreflight` y en `sendResponse`, que concentra las respuestas
  de todos los controladores y los errores explícitos.

Cabeceras aplicadas:

| Cabecera | Valor |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Frame-Options` | `DENY` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` |

No se agrega `unsafe-inline`. No se agrega `X-XSS-Protection`; la cabecera
estaba ausente y permanece ausente.

## Pruebas

Archivo: `test/java/com/CapaPresentacion/Controllers/BaseControllerCabecerasTest.java`.

Se agregaron **4 tests** (N=4), uno por clase de respuesta relevante:

1. Respuesta 2xx (200).
2. Respuesta 4xx (400).
3. Respuesta 5xx (500).
4. Preflight OPTIONS (204).

Cada test verifica las cinco cabeceras, que la CSP no contenga
`unsafe-inline` y que `X-XSS-Protection` no esté presente.

Comando: `mvn clean test`

Resultado: **365 tests, 0 fallos, 0 errores, 7 omitidos. BUILD SUCCESS.**
La base previa a este bloque era 361 tests (365 - 4). Los omitidos son
`PasarelaMercadoPagoTest` (dependencia de entorno) y 6 tests MySQL
(`IdentidadGlobalMySqlTest` y `ComprasMultiNegocioMySqlTest`) sin
`config.properties` local tras `mvn clean`.

## Alcance real de CSP

- No oculta código en DevTools: el HTML y el JS siguen siendo públicos.
- No reemplaza `escaparHtml` (BA-04).
- No protege el token de `sessionStorage`; eso corresponde a SF-07 y queda
  fuera de alcance.
- Sí impide la ejecución de código inyectado y bloquea iframes en las
  respuestas que llevan la cabecera.
- Límite actual: como el HTML no lo sirve el backend, la CSP no protege las
  páginas de la aplicación. Proteger el documento requiere servir el frontend
  con estas cabeceras o configurarlas en Caddy/Live Server, además de migrar
  Tailwind CDN, scripts y estilos inline, y definir la política para los CDNs.

## Verificación manual

Pendiente de confirmación del usuario:

1. Iniciar el backend y abrir la aplicación.
2. En DevTools, pestaña Red, filtrar las solicitudes `api`.
3. Verificar las cabeceras en respuestas 2xx, 4xx y 5xx, y la ausencia de
   `X-XSS-Protection`.
4. El documento `index.html`/`login.html` no mostrará estas cabeceras porque lo
   sirve Live Server/Caddy; no se esperan errores CSP en la consola porque el
   documento no tiene CSP en este alcance.

## Rollback

Cuando el cambio se confirme en un commit, revertir con `git revert <hash>`.
