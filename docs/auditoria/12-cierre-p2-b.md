# Cierre P2-B — ME-08 a ME-13

## Alcance

Remediación del bloque P2-B de la auditoría de seguridad: transaccionalidad de
la compra pública, mapeo global de excepciones, aislamiento de auditoría,
escaneo de secretos, rate limiting con bloqueo temporal e integración Stripe
con validación de firma de webhooks.

No se modificó la lógica de pagos del Bloque 2 salvo los endurecimientos
explícitos de ME-12/ME-13. No se ejecutaron pagos reales.

## Decisiones técnicas

- **Sin Spring**: el backend usa `jdk.httpserver`. No se introduce
  `@Transactional` ni `@ControllerAdvice`. El equivalente de `@Transactional` es
  `ITransaccion`/`JDBCTransaccion`: la compra pública pasa por
  `VentaService.crearVentaConPagoAsync`, que valida, descuenta stock y guarda la
  venta en una única transacción; el checkout externo se inicia después del
  commit. El equivalente de `@ControllerAdvice` es
  `BaseController.manejarError` + `ManejadorExcepcionesHttp`.
- **Excepciones HTTP**: los controladores ya no responden 500 genérico ante
  excepciones de validación/negocio: delegan en el mapeador global. Se agregó
  detección de duplicidad MySQL (`SQLState 23000`/código `1062`, incluso
  envuelta en `RuntimeException`) con respuesta 409.
- **Secretos**: `ConfigLoader.getSecretProperty` prioriza variable de entorno
  sobre archivo, manteniendo la system property primero para tests aislados.
  `STRIPE_SECRET_KEY` y `STRIPE_WEBHOOK_SECRET` se resuelven por esa vía.
- **Auditoría**: toda consulta exige `idNegocio > 0`; se eliminaron los métodos
  de compatibilidad que permitían listar sin filtro. Los logs neutralizan
  `\r`, `\n`, tabulaciones y separadores Unicode, tanto en el dominio
  (`Auditoria.formatearParaLog`) como en infraestructura (`SanitizadorLog.linea`
  y `SanitizadorLog.error`).
- **Rate limiting**: `LimitadorTasa` agrega `tiempoDeBloqueo` e
  `intentosPorIp` (`ConcurrentHashMap`) con `compute`/`merge`. Al expirar un
  bloqueo se elimina la clave de ambos mapas. La IP se obtiene de
  `X-Forwarded-For` solo a través de proxies confiables
  (`DireccionClienteResolver`). Los endpoints reales son
  `POST /api/auth/ingreso` y `POST /api/ventas/crear`; no existen `/login` ni
  `/checkout` literales.
- **Stripe**: se mantiene el SDK oficial y el `CreadorSesion` inyectable para
  tests. Connect y read timeout por defecto: 5000 ms. El webhook valida con
  `Webhook.constructEvent` y responde 400 ante
  `SignatureVerificationException`.

## Archivos modificados

### Código

- `src/com/CapaPresentacion/Controllers/BaseController.java`
- `src/com/CapaPresentacion/Controllers/ManejadorExcepcionesHttp.java`
- `src/com/CapaPresentacion/Controllers/VentaController.java`
- `src/com/CapaPresentacion/Controllers/AuthController.java`
- `src/com/CapaPresentacion/Controllers/ClienteController.java`
- `src/com/CapaPresentacion/Controllers/AuditoriaController.java`
- `src/com/CapaPresentacion/Controllers/CarritoController.java`
- `src/com/CapaPresentacion/Controllers/NegocioConfigController.java`
- `src/com/CapaPresentacion/Controllers/ProductoController.java`
- `src/com/CapaPresentacion/Controllers/RecomendacionController.java`
- `src/com/CapaPresentacion/Controllers/StockController.java`
- `src/com/CapaPresentacion/Utilidades/LimitadorTasa.java`
- `src/com/CapaInfraestructura/Configuracion/ConfigLoader.java`
- `src/com/CapaInfraestructura/Configuracion/SanitizadorLog.java`
- `src/com/CapaInfraestructura/Implementacion/PasarelaStripe.java`
- `src/com/CapaInfraestructura/Implementacion/RepositorioAuditoria.java`
- `src/com/CapaInfraestructura/Implementacion/MySqlRepositorioAuditoria.java`
- `src/com/CapaAplicacion/Service/StripeWebhookService.java`
- `src/com/CapaDominio/Entidades/Auditoria.java`

### Migración y operación

- `migracion_auditoria_negocio.sql` (verificada; ya existía)
- `scripts/scan-secrets.ps1` (exclusiones de `.git`, `target`,
  `node_modules`, `bin`, `build`, `dist`, `out`, `coverage` y binarios)
- `config.properties.example` (timeouts Stripe y límites de bloqueo)
- `docs/seguridad-operativa.md` (precedencia de secretos, variables de rate
  limiting y comando de escaneo)

### Tests

- `test/java/com/CapaPresentacion/Utilidades/LimitadorTasaTest.java`
- `test/java/com/CapaPresentacion/Controllers/ManejadorExcepcionesHttpTest.java`
- `test/java/com/CapaPresentacion/Controllers/StripeWebhookControllerTest.java`
- `test/java/com/CapaInfraestructura/Configuracion/SanitizadorLogTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/RepositorioAuditoriaTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/PasarelaStripeTest.java`
- `test/java/com/CapaDominio/Entidades/AuditoriaTest.java`
- `test/java/com/CapaAplicacion/Service/VentaServiceTest.java`

## Variables de entorno relevantes

| Variable | Uso | Valor por defecto |
|---|---|---|
| `STRIPE_SECRET_KEY` | Clave secreta Stripe | Sin valor; obligatoria si se usa Stripe |
| `STRIPE_WEBHOOK_SECRET` | Firma de webhooks Stripe | Sin valor; obligatoria para webhooks |
| `STRIPE_CONNECT_TIMEOUT_MS` | Timeout de conexión Stripe | 5000 |
| `STRIPE_READ_TIMEOUT_MS` | Timeout de lectura Stripe | 5000 |
| `RATE_LIMIT_AUTH_LOGIN_MAX_INTENTOS` | Intentos por IP antes del bloqueo de login | 5 |
| `RATE_LIMIT_AUTH_LOGIN_BLOQUEO_SEGUNDOS` | Duración del bloqueo de login | 60 |
| `RATE_LIMIT_CHECKOUT_MAX_INTENTOS_IP` | Intentos por IP antes del bloqueo de checkout | 20 |
| `RATE_LIMIT_CHECKOUT_BLOQUEO_SEGUNDOS` | Duración del bloqueo de checkout | 60 |

## Verificación

- `mvn -q test`: **345 tests, 0 fallos, 0 errores, 1 omitido**.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\scan-secrets.ps1`:
  sin hallazgos en 541 archivos trackeados.
- Cobertura específica:
  - concurrencia de 2 hilos sobre el rate limiter: exactamente el máximo de
    intentos permitidos;
  - liberación de bloqueo y eliminación de contadores al expirar;
  - firma Stripe inválida: 400; firma válida: 200;
  - duplicidad SQL envuelta: 409;
  - validación de usuario: 400;
  - auditoría sin `id_negocio` válido: rechazo;
  - CRLF en logs de auditoría: neutralizado;
  - checkout Stripe con timeouts de 5000 ms;
  - checkout externo ejecutado fuera de la transacción.
- Los tests de concurrencia/rollback de stock preexistentes
  (`VentaServiceConcurrenciaTest`, `VentaServiceCancelacionTest`) siguen verdes.

## Pendientes y límites

- No se hicieron llamadas reales a Stripe; el E2E usa un creador de sesiones
  falso y la firma de webhook se valida con HMAC local.
- La integración MySQL depende del entorno; en esta corrida se mantuvo el
  omitido habitual de la suite.
- El rate limiting es en memoria, por instancia. Para múltiples instancias se
  requiere un almacén compartido (Redis) en una fase posterior.
