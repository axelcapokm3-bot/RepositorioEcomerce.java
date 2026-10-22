# Pruebas y verificación

## Entorno y comandos

- Java 21, Maven, MySQL 8.3.0 y Redis deben estar disponibles para los tests de integración.
- El proyecto no incluye Maven Wrapper; se usa `mvn` del sistema.
- Directorio de tests: `test/java` (configurado en `pom.xml:65`).
- Comando base:

```powershell
mvn test
```

- Comando de un solo test:

```powershell
mvn -Dtest=AutenticacionServiceTest test
```

- Varios tests:

```powershell
mvn -Dtest=VentaServiceTest,PagoServiceTest,VentaServiceConcurrenciaTest test
```

- Los reportes quedan en `target/surefire-reports/` (XML y TXT). No versionar `target/`.
- No usar la base de producción ni credenciales reales. Para MySQL/Redis usar bases/contenedores desechables y variables de entorno; no imprimir `config.properties`.

## Línea base verificada (2026-10-21)

| Métrica | Resultado |
|---|---:|
| Suites | 41 |
| Tests | 216 |
| Fallos | 0 |
| Errores | 0 |
| Omitidos | 1 |
| Archivos de test trackeados | 43 |
| Archivos vacíos (sin reporte) | 2 |

El test omitido está en `PasarelaMercadoPagoTest` (6 tests, 1 skipped). Los dos archivos vacíos son:

- `test/java/com/CapaAplicacion/Service/StripeWebhookServiceTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/PasarelaStripeTest.java`

**Lectura correcta**: la suite pasa, pero no valida Stripe ni los escenarios críticos de identidad, replay, stock y multi-tenant. Un resultado verde no equivale a autorización para producción.

## Distribución observada

| Área | Suites | Tests |
|---|---:|---:|
| Dominio | 11 | 57 |
| Servicios de aplicación | 16 | 111 |
| Infraestructura | 8 | 30 |
| Presentación | 2 | 8 |
| Mappers/DTO/Seguridad | 4 | 10 |
| **Total** | **41** | **216** |

La distribución es aproximada por paquete de test; los totales exactos son los de la tabla de línea base.

## Pruebas existentes relevantes

| Test | Tests | Cobertura útil | Límite |
|---|---:|---|---|
| `AutenticacionServiceTest` | 21 | Registro, login, vínculos y roles en unitario/mock. | No demuestra takeover real por DNI/email ni revocación de sesión. |
| `VentaServiceTest` | 20 | Creación, stock, cancelación y estados. | No cubre checkout rechazado por pasarela, expiración ni click de compra con producto inactivo. |
| `VentaServiceConcurrenciaTest` | 2 | Concurrencia básica de stock. | No cubre reserva con TTL, reintentos de pasarela ni múltiples instancias. |
| `PagoServiceTest` | 9 | Inicio de checkout y estados. | No cubre replay de venta cancelada/pagada ni eventos fuera de orden. |
| `WebhookServiceFanoutTest` | 8 | Fan-out básico. | No cubre fallo de un tenant, persistencia durable ni reinicio. |
| `WebhookServicePrimeraEntregaTest` | 13 | Primera entrega de Order. | No cubre dos negocios con tokens distintos ni ACK prematuro. |
| `PasarelaMercadoPagoTest` | 6 (1 skip) | URL/credenciales y flujo básico. | Un caso omitido; no cubre firma/tiempo real. |
| `PasarelaStripeTest` | **0** | Ninguna. | Archivo vacío. |
| `StripeWebhookServiceTest` | **0** | Ninguna. | Archivo vacío. |
| `RedisCarritoRepositoryTest` | 4 | Redis real, TTL y persistencia. | Requiere Redis; no cubre carrito multi-instancia bajo fallo. |
| `IdentidadGlobalMySqlTest` | 2 | Identidad global contra MySQL real. | No cubre los flujos de abuso de `CR-01/CR-02`. |

## Matriz de pruebas de regresión requerida

Cada fila debe implementarse como test que **falla antes del fix** y pasa después. Cuando el caso requiera pasarela, usar sandbox o doble de prueba; no llamar APIs reales en CI.

### Identidad y multi-tenant

| ID | Caso | Tipo | Resultado esperado |
|---|---|---|---|
| T-CR-01a | Registro con DNI de cliente existente no vinculado | Integración | `403/409`, sin token, sin vínculo. |
| T-CR-01b | `vincularNegocio` con `idCliente` de otro usuario | Integración | Rechazo; no se crea vínculo. |
| T-CR-01c | Titular legítimo completa invitación | Integración | Vínculo exitoso y auditable. |
| T-CR-02a | Admin A registra email de víctima | Integración | Sin sesión para la víctima; invitación o rechazo. |
| T-CR-02b | Víctima con vínculo en tenant B | Integración | Admin A no puede cambiar al tenant B. |
| T-ME-01a | Fallo al guardar vínculo | Integración | Rollback de cuenta/cliente; sin huérfanos. |
| T-ME-01b | Email existente vs. inexistente | API | Respuesta no enumerable/rate-limited. |
| T-ME-02a | Segundo uso del token bootstrap | Integración | Rechazo y auditoría. |

### Pagos y webhooks

| ID | Caso | Tipo | Resultado esperado |
|---|---|---|---|
| T-AL-01a | Checkout de venta cancelada | Integración | `409`; no se devuelve URL. |
| T-AL-01b | Webhook aprobado después de cancelar | Integración | Pago no queda aprobado sin decisión de reembolso. |
| T-AL-02a | `APROBADO` seguido de `PENDIENTE` | Unitario/Integración | No degrada. |
| T-AL-02b | Dos webhooks concurrentes | Integración | Estado final consistente; una sola transición. |
| T-AL-03a | Fallo de persistencia antes del ACK | API | `5xx`, no `200`. |
| T-AL-03b | Reinicio con evento en cola | Integración | Evento se reprocesa desde inbox. |
| T-AL-04a | Dos tenants, notificación de Order inicial | Integración | Se resuelve el tenant correcto. |
| T-AL-04b | Un tenant sin pago local | Integración | No se corta el fan-out; se reintenta o registra. |
| T-ME-07a | Firma MP con `ts` viejo | Unitario | Rechazo por frescura. |
| T-ME-11a | Secreto de tenant B en endpoint A | Integración | Rechazo por tenant. |

### Catálogo, stock e idempotencia

| ID | Caso | Tipo | Resultado esperado |
|---|---|---|---|
| T-AL-05a | Producto inactivo en catálogo | API | No aparece. |
| T-AL-05b | Compra directa de producto inactivo | API | `404/409`. |
| T-AL-08a | Fallo de pasarela tras descontar stock | Integración | Stock liberado. |
| T-AL-08b | Reserva vencida | Integración | Job libera stock y cancela venta. |
| T-AL-08c | Dos compradores concurrentes | Integración | Sin sobreventa; uno falla limpio. |
| T-AL-09a | Misma clave en dos negocios | Integración | Ambas ventas se crean. |
| T-ME-08a | Fallo del UPDATE de venta | Integración | Detalles preservados. |

### Sesión, tenant y API

| ID | Caso | Tipo | Resultado esperado |
|---|---|---|---|
| T-AL-06a | Token tras desactivar cuenta | API | `401` inmediato. |
| T-AL-06b | Token tras cambiar rol | API | Permisos nuevos, no antiguos. |
| T-AL-07a | Negocio desactivado tras cachear | Unitario/Integración | Resolución falla sin reiniciar. |
| T-ME-03a | Error con `\`, `"`, `\n` | Unitario | JSON válido. |
| T-ME-04a | 10k claves únicas | Unitario | No expulsa ventana activa. |
| T-ME-05a | Archivo/env/system distintos | Unitario | Precedencia única y documentada. |
| T-ME-06a | URL Stripe `http://evil` | Unitario | Rechazo. |
| T-ME-09a | `PUT/DELETE` ID inexistente | API | `404`, no `500/200`. |
| T-ME-10a | Auditoría por producto | Integración | Solo eventos del producto. |
| T-ME-12a | Cliente lento/saturación | Carga | Sin agotamiento indefinido; rechazo/cierre. |
| T-BA-01a | Rollback que falla | Unitario | Excepción original conservada. |
| T-BA-04a | Descripción con HTML | Frontend/E2E | Se muestra como texto. |
| T-BA-05a | Update/delete ID inexistente | Integración | Fila 0 detectada; error de dominio. |

## Pruebas E2E en sandbox

No se ejecutaron en esta auditoría. Antes de un nuevo veredicto:

1. Levantar MySQL/Redis limpios y aplicar migraciones desde cero.
2. Configurar Mercado Pago sandbox y Stripe test keys; no usar credenciales de producción.
3. Ejecutar: registro → catálogo → carrito → checkout → webhook aprobado → venta pagada → stock consistente.
4. Repetir con pago rechazado, cancelación, abandono, reintento y webhook duplicado.
5. Verificar que un negocio B no ve datos ni pagos del negocio A.
6. Probar dos instancias del servidor para detectar problemas de caché/rate limit/executor.
7. Documentar evidencia: comando, versión, resultado y logs sin secretos.

## Criterios de salida

- `mvn test` completo en verde, con los tests nuevos y sin archivos vacíos.
- Todos los tests de la matriz P0/P1 presentes y estables.
- E2E sandbox aprobado para MP y Stripe.
- Prueba de carga básica sin agotamiento del pool HTTP.
- Reporte de cobertura revisado por área, no solo porcentaje global.
- Ningún test depende de secretos reales ni de datos de producción.
