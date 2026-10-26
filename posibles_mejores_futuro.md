# Posibles mejoras futuras

**Fecha:** 2026-10-24
**Alcance:** roadmap posterior al cierre de la remediación P3.
**Naturaleza:** análisis y priorización; este documento no implementa cambios.

## Estado Actual

### Arquitectura y operación

- Backend Java 21 con Maven, organizado por capas (`CapaDominio`, `CapaAplicacion`, `CapaInfraestructura`, `CapaPresentacion`, `CapaExterna`).
- Servidor HTTP nativo de Java SE (`jdk.httpserver`), sin Spring. La composición manual se concentra en `src/com/CapaExterna/ServerMain.java`.
- Persistencia principal en MySQL mediante JDBC/HikariCP y repositorios propios. El carrito usa Redis con TTL y `WATCH`/`MULTI` para concurrencia optimista (`RedisCarritoRepository`).
- Procesamiento asíncrono con `CompletableFuture` y `ThreadPoolExecutor` por tipo de servicio (`ServiceExecutor`, `TipoServicio`). Los webhooks tienen outbox durable en MySQL con reintentos (`WebhookEventoService`, `ProcesadorEventosWebhook`, `JobWebhooks`).
- Pagos con Mercado Pago y Stripe, validación de firma de webhooks, idempotencia y estados de pago/venta.
- Seguridad de acceso con tokens, filtros por negocio, PBKDF2-HMAC-SHA256 con 210.000 iteraciones y rate limiting en memoria.
- Controles existentes destacables: uso general de `PreparedStatement`, aislamiento por `id_negocio`, idempotencia de checkout, outbox de webhooks, sanitización de logs y mapeo centralizado de excepciones HTTP.

### Verificación al cierre

| Evidencia | Resultado |
|---|---|
| `mvn test` | **345 tests, 0 fallos, 0 errores, 1 omitido** |
| Línea base documentada | 216 tests, 41 suites, 1 omitido |
| Delta | +129 tests |
| Archivos de test | 65 archivos |
| Estado del build | `BUILD SUCCESS` |
| Test omitido | `PasarelaMercadoPagoTest` (dependencia de entorno) |

El total de 345 tests coincide con el cierre P2-B. La remediación P3 agregó los cambios BA-01, BA-03, BA-04, BA-05 y BA-06 sin alterar el conteo de tests. BA-02 se mantiene como fail-fast aceptado y documentado: la inicialización de HikariCP falla al arranque si la base no está disponible.

No se ejecutaron pagos reales ni pruebas E2E contra sandbox de Stripe o Mercado Pago. La cobertura de pasarelas es unitaria/integracion con mocks o clientes falsos. El esquema MySQL inspeccionado no tiene todavía `auditoria.id_negocio`; la migración `migracion_auditoria_negocio.sql` debe aplicarse en el entorno correspondiente antes de desplegar consultas de auditoría multi-negocio.

### Fortalezas actuales

- Separación razonable entre dominio, aplicación, infraestructura y presentación.
- Repositorios con filtros de negocio y pruebas de aislamiento.
- Outbox de webhooks y estados explícitos para pagos.
- Uso consistente de transacciones para venta/stock.
- Controles de seguridad ya incorporados: firma de webhooks, rate limiting, revocación de tokens, sanitización de logs y escaneo de secretos.

### Riesgos residuales

- Listados completos sin paginación y un N+1 en el listado de ventas.
- Índices de auditoría incompletos para consultas por negocio y fecha.
- Estado de ejecución, caché y rate limiting local por instancia.
- Notificaciones por email sin outbox ni reintentos.
- Clases de servicio y controladores con demasiadas responsabilidades.
- Dependencias de la capa de aplicación hacia infraestructura.
- Falta de E2E real de pasarelas y de pruebas de regresión específicas para los fixes P3.

## Mejoras de Rendimiento

| ID | Prioridad | Mejora | Evidencia | Acción propuesta |
|---|---|---|---|---|
| RF-01 | **Alta** | Eliminar N+1 al listar ventas | `MySqlRepositorioVenta.listarTodas` (`:151-168`) llama a `cargarDetalles` (`:249`) por cada venta. | Cargar detalles con un `JOIN` y mapear en una sola consulta, o usar un lote `WHERE id_venta IN (...)`. Medir con `EXPLAIN` y prueba de carga. |
| RF-02 | **Alta** | Paginar listados | `ProductoService.listarTodosAsync`, `ClienteService.listarTodos`, listado de ventas y `AuditoriaService.obtenerTodosAsync` (`:92-99`). Auditoría tiene un tope fijo de 1000 sin paginación. | Agregar `limit`/`offset` o cursor, tamaño máximo por página y filtros. Exponer metadatos de paginación en la API. |
| RF-03 | **Alta** | Alinear índices con las consultas reales | La tabla `auditoria` inspeccionada no tiene `id_negocio` y solo tiene índices globales (`idx_accion`, `idx_entidad`, `idx_fecha`, `idx_usuario`). `migracion_auditoria_negocio.sql` sigue pendiente de aplicar. | Aplicar la migración y crear índices compuestos `(id_negocio, fecha)` y `(id_negocio, entidad, id_entidad, fecha)`. Revisar `pagos` para consultas por negocio/estado/fecha. |
| RF-04 | **Media** | Reducir llamadas del Dashboard | El resumen del Dashboard combina listados de productos, clientes, ventas y auditoría en llamadas separadas. | Crear un endpoint de resumen agregado o cargar secciones de forma diferida. Cachear catálogo/configuración con invalidación explícita. |
| RF-05 | **Media** | Consultar el pago pendiente con filtro SQL | `PagoService.buscarPagoPendiente` (`:372-379`) carga los pagos de la venta y filtra en memoria. | Consultar `WHERE id_venta = ? AND id_negocio = ? AND estado = 'PENDIENTE' LIMIT 1`. |
| RF-06 | **Media** | Optimizar expiración de ventas | `VentaService.expirarVentasPendientes` (`:484-512`) procesa una transacción por venta candidata. | Mantener el lote, pero agrupar actualizaciones cuando el volumen sea alto y medir el costo de `reintegrarStockDeVenta`. |
| RF-07 | **Baja** | Cachear catálogo y configuración de negocio | Las listas de productos y configuraciones se consultan repetidamente para renderizar y resolver tenants. | Cache Redis con TTL corto e invalidación al crear/actualizar/eliminar productos o configuración. |
| RF-08 | **Baja** | Revisar serialización y tamaño de payloads | Respuestas de listados completos y auditoría pueden crecer aunque exista un tope fijo. | Limitar campos en listados, separar detalle de resumen y comprimir respuestas si el proxy lo permite. |

## Mejoras de Escalabilidad

| ID | Prioridad | Mejora | Evidencia | Acción propuesta |
|---|---|---|---|---|
| ES-01 | **Alta** | Externalizar trabajos y colas | `ServiceExecutor` usa `ThreadPoolExecutor` con colas en memoria; `TipoServicio` fija tamaños y capacidades (VENTA 5/10/100, etc.). | Usar una cola durable (RabbitMQ/Kafka) o outbox en MySQL para trabajos críticos. Mantener `CallerRunsPolicy` solo como backpressure local. |
| ES-02 | **Alta** | Rate limiting distribuido | `LimitadorTasa` usa mapas estáticos por JVM y un máximo de 10.000 claves. | Migrar ventanas y bloqueos a Redis con `INCR`/`EXPIRE` o script Lua. Mantener límites locales como segunda capa. |
| ES-03 | **Alta** | Caché de resolución de negocio compartida | `ResolutorNegocio` usa un `ConcurrentHashMap` estático con TTL de 60 segundos. | Usar Redis con invalidación por evento o TTL corto; publicar invalidaciones entre instancias. |
| ES-04 | **Media** | Outbox para notificaciones | `NotificacionListener` ejecuta el envío en un executor; no hay persistencia, reintento ni dead-letter. | Persistir notificaciones pendientes y procesarlas con reintentos/backoff y estado final. |
| ES-05 | **Media** | Evitar enumerar todos los negocios por webhook | `StripeWebhookService.buscarSecretos` recorre `listarActivos()` y consulta configuraciones por negocio; `WebhookService.tokensAccesoDistintos` también recorre activos. | Indexar secretos/tokens por hash o por identificador de cuenta; cachear la resolución y renovarla por evento. |
| ES-06 | **Media** | Jobs seguros en múltiples instancias | `JobExpiracionVentas` y `JobWebhooks` se inician en cada proceso. | Agregar lock distribuido o leader election para que solo una instancia ejecute cada job. |
| ES-07 | **Media** | Configurar el executor HTTP | `ServerMain` crea el executor HTTP con valores propios; el puerto está fijo en 8080. | Externalizar puerto, hilos y colas a configuración; agregar readiness/liveness. |
| ES-08 | **Baja** | Límites de carrito | El carrito Redis tiene TTL de 24 horas y reintentos de concurrencia, pero no un máximo explícito de ítems. | Definir máximo de líneas y cantidad por producto; validar antes de escribir en Redis. |
| ES-09 | **Baja** | Dimensionamiento de Hikari y pools | Los comentarios de `TipoServicio` relacionan pools con porcentajes de Hikari, pero los valores están hardcodeados. | Externalizar tamaños y validar que la suma de pools no agote el pool de base. |
| ES-10 | **Baja** | Aislamiento de tenants en recursos compartidos | Rate limit, caché de negocio y jobs son globales por proceso. | Incluir `id_negocio` en claves de caché/límites y revisar métricas por tenant. |

## Refactorización de Código

| ID | Prioridad | Mejora | Evidencia | Acción propuesta |
|---|---|---|---|---|
| RC-01 | **Alta** | Dividir servicios y controladores grandes | `VentaService` tiene 537 líneas, `PagoService` 361 y `VentaController` 263. | Separar checkout, consulta de ventas, expiración, aplicación de estados de pago y orquestación HTTP, preservando los límites transaccionales actuales. |
| RC-02 | **Alta** | Invertir dependencias aplicación → infraestructura | `VentaService` importa `RegistroExecutors`; `PagoService` importa `PasarelaFactory`; servicios de webhook importan `ConfigLoader`, `SanitizadorLog` y `RegistroExecutors`. | Mover esas dependencias detrás de interfaces de aplicación y resolverlas en el composition root (`ServerMain`). |
| RC-03 | **Media** | Reemplazar singletons estáticos | `RegistroExecutors`, `ResolutorNegocio`, `LimitadorTasa` y `PasarelaFactory` mantienen estado estático. | Inyectar instancias por constructor y reservar el estado global para un composition root explícito. |
| RC-04 | **Media** | Unificar el patrón de repositorios | BA-05 ya valida filas afectadas en clientes y en `Producto.eliminar`, pero el patrón se repite con variantes. | Extraer helpers `exigirUnaFila`/`exigirFila` y estandarizar `RecursoNoEncontradoException`. |
| RC-05 | **Media** | Externalizar `TipoServicio` | Los tamaños de core, máximo y cola están hardcodeados en el enum. | Configurar por entorno y validar límites al arrancar. |
| RC-06 | **Media** | Reducir constructores largos | Varios servicios reciben muchas dependencias y crean otras internamente. | Usar fábricas de composición por caso de uso; evaluar un contenedor liviano solo si el equipo lo aprueba. |
| RC-07 | **Baja** | Unificar mapeo de errores | Existen respuestas y wrappers con estilos distintos entre controladores y repositorios. | Consolidar en `ManejadorExcepcionesHttp` y `BaseController`, con pruebas de contrato HTTP. |
| RC-08 | **Baja** | Normalizar formato y nombres | Hay diferencias de indentación y nombres (`map` en `MySqlRepositorioProducto`, variables `e`/`exception`). | Aplicar el formato acordado y revisar en CI; evitar mezclar refactor cosmético con cambios funcionales. |
| RC-09 | **Baja** | Limpiar documentación y migraciones obsoletas | `migracion_identidad_global.sql` sigue trackeado; `DocumentacionBaseDeDatos.txt` describe un esquema legacy. | Mover a `docs/legacy/` con README, actualizar contratos y eliminar referencias ambiguas. |

## Seguridad Futura

| ID | Prioridad | Mejora | Evidencia | Acción propuesta |
|---|---|---|---|---|
| SF-01 | **Alta** | Endurecer cabeceras y superficie del frontend | BA-04 quedó corregido con `escaparHtml`, pero el token se conserva en `sessionStorage`. | Agregar CSP, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors` y evaluar cookies `HttpOnly`/`Secure` con CSRF. |
| SF-02 | **Alta** | Automatizar SCA y secret scanning | Existe `scripts/scan-secrets.ps1`, pero no se observó un análisis de dependencias en CI. | Ejecutar escaneo de secretos y OWASP Dependency-Check en cada PR; fijar y actualizar Stripe, Mercado Pago, Jackson y MySQL. |
| SF-03 | **Media** | Evolucionar el hash de contraseñas | PBKDF2 210k es aceptable y quedó documentado como decisión de hardening. | Evaluar Argon2id o PBKDF2 ≥600k, limitar iteraciones aceptadas y rehashear en el próximo login exitoso. |
| SF-04 | **Media** | Rate limiting y MFA para administradores | El rate limiting es local y no hay MFA observado. | Distribuir límites en Redis, agregar bloqueo por cuenta/IP y MFA para operaciones administrativas. |
| SF-05 | **Media** | Replay y rotación de webhooks | El outbox y la firma existen; la tolerancia de timestamp y los secretos por tenant requieren operación explícita. | Definir ventana de replay, rotación de secretos, dead-letter y alertas por reintentos agotados. |
| SF-06 | **Media** | Completar aislamiento y retención de auditoría | El esquema inspeccionado no tiene `auditoria.id_negocio`; la migración está pendiente. | Aplicar `migracion_auditoria_negocio.sql`, crear índices por negocio/fecha y definir retención/exportación inmutable. |
| SF-07 | **Media** | Endurecer sesión y revocación | Hay revocación de tokens y TTL, pero el almacenamiento del token en el navegador sigue siendo sensible a XSS. | Acortar TTL, rotar refresh tokens, revocar por dispositivo y evaluar cookies seguras. |
| SF-08 | **Baja** | Diagnóstico de arranque y salud | BA-02 mantiene fail-fast de HikariCP; el mensaje puede no ser accionable. | Agregar logs estructurados, códigos de salida y endpoints de health/readiness. |
| SF-09 | **Baja** | Higiene de secretos locales | `config.properties` local no está trackeado, pero puede contener credenciales reales. | Confirmar que sigue fuera de Git, rotar credenciales si fueron reales y documentar la precedencia de secretos. |
| SF-10 | **Baja** | Limpieza documental y migraciones | Persisten documentos legacy y una migración obsoleta. | Mover a `docs/legacy/`, marcar “no usar” y actualizar `API_DOCUMENTATION.md` y `DocumentacionBaseDeDatos.txt`. |
| SF-11 | **Baja** | Límites de request y timeouts | El servidor nativo no muestra límites explícitos de tamaño de request. | Definir límites de body, timeouts de lectura/escritura y respuestas 413 controladas. |

## Priorización resumida

### Alta prioridad

- RF-01, RF-02, RF-03: N+1, paginación e índices/migración de auditoría.
- ES-01, ES-02, ES-03: colas durables, rate limiting distribuido y caché de negocio compartida.
- RC-01, RC-02: dividir servicios grandes e invertir dependencias hacia infraestructura.
- SF-01, SF-02: CSP/cabeceras y SCA/secret scanning automatizado.

### Media prioridad

- RF-04, RF-05, RF-06: resumen del Dashboard, pago pendiente y expiración.
- ES-04, ES-05, ES-06: outbox de notificaciones, resolución de secretos y jobs multi-instancia.
- RC-03, RC-04, RC-05, RC-06: singletons, repositorios, configuración de pools y constructores.
- SF-03, SF-04, SF-05, SF-06, SF-07: hashing, MFA, webhooks, auditoría y sesión.

### Baja prioridad

- RF-07, RF-08: caché de catálogo y tamaño de payloads.
- ES-07, ES-08, ES-09, ES-10: executor HTTP, carrito, Hikari y claves por tenant.
- RC-07, RC-08, RC-09: errores, formato y documentación legacy.
- SF-08, SF-09, SF-10, SF-11: arranque, secretos locales, documentación y límites de request.

## Roadmap sugerido

1. **Ahora (0-30 días):** RF-01, RF-02, RF-03 y completar la migración de auditoría. Medir con `EXPLAIN` y pruebas de carga.
2. **Siguiente (1-3 meses):** ES-01 a ES-03, ES-06, RC-01 y RC-02. Introducir Redis/cola solo con operación y observabilidad definidas.
3. **Después (3-6 meses):** outbox de notificaciones, MFA, CSP, SCA en CI y limpieza documental/migraciones legacy.

Ninguna de estas mejoras corrige una regresión de los hallazgos P3 cerrados; son evolución arquitectónica y operativa. El veredicto de cierre se mantiene en `docs/auditoria/05-decisiones-pendientes.md`.
