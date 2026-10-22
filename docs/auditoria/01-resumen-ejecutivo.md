# Resumen ejecutivo

## Veredicto

**RECHAZADO para producción.**

La aplicación compila, la suite pasa y los flujos principales están implementados. Sin embargo, la auditoría encontró **dos fallos críticos de toma de identidad**, **nueve riesgos altos** con impacto directo en dinero, stock o aislamiento entre negocios, y **trece riesgos medios** de operación y consistencia. Los tests verdes no cubren esos escenarios: en particular, los archivos de test de Stripe webhook y Stripe pasarela están vacíos.

La recomendación es:

- **No desplegar a producción ni procesar pagos reales** en el estado auditado.
- Usar el entorno local para desarrollo, con datos ficticios y pasarelas en modo de prueba.
- Cerrar los P0 y P1 del plan de remediación, añadir las pruebas de regresión y repetir la revisión adversarial antes de solicitar un nuevo veredicto.

## Por qué el veredicto es RECHAZADO

1. **Toma de control de cuentas y perfiles ajenos.** El registro reutiliza un `Cliente` existente por DNI sin demostrar propiedad. `vincularNegocio` permite reclamar cualquier perfil de cliente libre del negocio. Un atacante puede acceder a datos y compras de otra persona.
2. **Impersonación cross-tenant por email.** Un administrador puede registrar un email ya existente como `ADMIN`, obtener una sesión de esa cuenta y cambiar de negocio si la víctima tiene vínculos en otros tenants. Es un fallo de aislamiento multi-tenant.
3. **Pagos reutilizables o inconsistentes.** Un checkout existente se devuelve sin validar que la venta siga pendiente; una venta cancelada o ya pagada puede volver a recibir un pago. Los webhooks pueden degradar el estado del pago y los eventos se confirman con `200` antes de un procesamiento no durable.
4. **Productos inactivos comprables.** El catálogo público y la compra directa no filtran `activo`, por lo que un producto dado de baja puede listarse y venderse.
5. **Sesiones sin revocación efectiva.** El token HMAC no consulta cuenta, vínculo, rol ni negocio activo. Una cuenta bloqueada, un rol degradado o un negocio dado de baja conservan acceso hasta 12 horas; el caché de tenant no se invalida.
6. **Stock retenido sin compensación.** La venta y el stock se persisten antes de crear el checkout; si la pasarela falla o el pago queda pendiente, no hay expiración ni liberación automática.

## Métricas

| Indicador | Valor |
|---|---:|
| Hallazgos | 30 |
| Críticos | 2 |
| Altos | 9 |
| Medios | 13 |
| Bajos | 6 |
| Tests | 216 |
| Fallos / errores | 0 / 0 |
| Omitidos | 1 |
| Reportes surefire | 41 |
| Tests MySQL ejecutados | 2 |
| Tests Redis ejecutados | 4 |
| Cobertura Stripe webhook/pasarela | 0 tests (archivos vacíos) |

## Mapa de riesgo por área

| Área | Nivel | Motivo |
|---|---|---|
| Identidad y multi-tenant | **Crítico** | Reclamo de perfiles por DNI, email existente e impersonación cross-tenant. |
| Pagos y webhooks | **Alto** | Replay de checkout, degradación de estado, ACK prematuro, pérdida de eventos. |
| Catálogo y stock | **Alto** | Productos inactivos vendibles; reserva de stock sin expiración. |
| Autorización de sesión | **Alto** | Token sin revocación ni verificación de estado; caché de tenant obsoleto. |
| Consistencia de datos | **Medio** | Idempotencia global, borrado de detalles antes del update, checks incompletos. |
| Infraestructura HTTP | **Medio** | Rate limit local, sin proxy consciente, sin timeouts de request. |
| Configuración y secretos | **Medio** | Precedencias inconsistentes; config local con secretos; documentación operativa parcialmente desactualizada. |
| Frontend | **Bajo/Medio** | Token en `sessionStorage`, XSS residual en el resumen de auditoría y checkout pendiente. |
| Pruebas | **Medio** | Suite verde, pero sin Stripe real, sin E2E, sin casos negativos de identidad ni de concurrencia. |

## Qué está bien

- Arquitectura por capas recognizable y dominio con transiciones de `EstadoVenta` protegidas.
- SQL parametrizado en los repositorios revisados; no se encontró inyección SQL directa.
- Contraseñas PBKDF2 con sal aleatoria y comparación en tiempo constante; login con hash falso para reducir enumeración temporal.
- CORS con allowlist de orígenes, cabecera `Authorization` explícita y sin cookies de sesión.
- Carrito Redis con TTL de 24 horas y mutaciones transaccionales con reintentos.
- URLs de retorno de Mercado Pago exigidas por HTTPS y host; validación de firma HMAC del webhook.
- Suite de 216 tests con integración real a MySQL y Redis, sin fallos.

## Condiciones para cambiar el veredicto

El veredicto solo puede pasar a **APROBADO CON RIESGOS** o **APROBADO** cuando se cumplan, como mínimo:

1. Cerrar `CR-01` y `CR-02` con pruebas de regresión que reproduzcan el abuso y demuestren el rechazo.
2. Cerrar los altos de pagos (`AL-01` a `AL-04`), stock (`AL-08`) e idempotencia (`AL-09`).
3. Implementar revocación/verificación de sesión (`AL-06`) e invalidación de tenant (`AL-07`).
4. Corregir catálogo y compra de productos inactivos (`AL-05`).
5. Añadir tests de Stripe webhook/pasarela, auth negativa, concurrencia de stock y estados de pago.
6. Ejecutar una prueba E2E en sandbox de pasarelas y una revisión independiente del diff de remediación.

## Decisión solicitada

La decisión pendiente no es técnica sino de alcance: **aprobar o no la implementación de las remediaciones P0/P1** descritas en `03-plan-remediacion.md`. Hasta esa aprobación, este documento debe tratarse como diagnóstico y no como autorización de cambios en producción.
