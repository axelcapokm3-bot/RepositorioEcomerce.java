# Plan de remediación

## Principios

- **No implementar todavía**: este plan requiere aprobación explícita. No modifica código, esquema, configuración ni pagos por sí mismo.
- **Primero reproducir, después corregir**: cada fix debe tener una prueba que falle antes y pase después.
- **Migraciones aditivas y reversibles**: columnas/tablas nuevas primero, backfill después, constraints al final. Evitar `DROP`/`ALTER` destructivos en una sola ventana.
- **Cambios pequeños y revisables**: un PR por bloque lógico, con diff acotado y pruebas propias.
- **Compatibilidad**: no romper contratos HTTP existentes sin versionado o periodo de transición.
- **Observabilidad**: logs estructurados sin secretos, métricas de pagos/stock/webhooks y alertas antes de producción.

## Prioridades

| Prioridad | Significado | Hallazgos |
|---|---|---|
| **P0** | Bloquea producción y pagos reales | `CR-01`, `CR-02`, `AL-01`, `AL-02`, `AL-03`, `AL-04` |
| **P1** | Debe cerrarse antes de un go-live controlado | `AL-05`, `AL-06`, `AL-07`, `AL-08`, `AL-09`, `ME-01`, `ME-02`, `ME-09`, `ME-13` |
| **P2** | Robustez, operación e higiene; puede ir en paralelo | `ME-03`, `ME-04`, `ME-05`, `ME-06`, `ME-07`, `ME-08`, `ME-10`, `ME-11`, `ME-12`, `BA-01` a `BA-06` |

## Secuencia recomendada

### Fase 0 — Preparación y reproducción

- Congelar el alcance y crear una rama de remediación.
- Añadir pruebas de regresión que reproduzcan `CR-01`, `CR-02`, `AL-01`, `AL-02`, `AL-05` y `AL-09`; deben fallar en el estado actual.
- Crear un entorno de staging con MySQL/Redis aislados y pasarelas sandbox.
- Definir backup/restore y plan de rollback antes de tocar esquema.
- **Salida**: suite de regresión roja documentada; línea base de métricas.

### Fase 1 — Identidad y multi-tenant (P0)

**Hallazgos**: `CR-01`, `CR-02`, `ME-01`, `ME-02`.

1. Crear un flujo de invitación con token de un solo uso para vincular un cliente existente; nunca reutilizar por DNI automáticamente.
2. Hacer `vincularNegocio` una operación que requiera prueba de titularidad o aprobación administrativa auditada; validar que el `idCliente` no esté vinculado a otra cuenta activa.
3. Prohibir que el registro admin emita sesión para una cuenta existente. Si el email existe, responder genéricamente o crear invitación pendiente; nunca devolver un token ajeno.
4. Envolver cuenta+cliente+vínculo en una transacción única.
5. Token bootstrap: un solo uso, expiración, auditoría y bloqueo tras crear el primer admin.
6. Filtrar vínculos activos en `existeVinculo`; definir reactivación controlada.
7. Añadir pruebas de takeover por DNI, por email, por `idCliente` ajeno y de cambio de negocio.

**Criterio de aceptación**: ningún flujo permite operar con un `idCliente`/`idCuenta` que el solicitante no haya probado poseer; las pruebas de regresión pasan; no se emite sesión para cuentas existentes en el registro.

**Rollback**: las invitaciones nuevas son aditivas; desactivar el flujo nuevo y volver al anterior solo si no se han emitido invitaciones válidas. No eliminar vínculos existentes sin backup.

### Fase 2 — Integridad de pagos y webhooks (P0)

**Hallazgos**: `AL-01`, `AL-02`, `AL-03`, `AL-04`, `ME-07`, `ME-11`.

1. Introducir una tabla inbox de webhooks: `id_evento`, `proveedor`, `negocio`, `payload`, `estado`, `intentos`, `recibido_en`, `procesado_en`, con unicidad por proveedor/evento.
2. Persistir el evento antes de responder `200`; procesar de forma asíncrona durable con reintentos y dead-letter. Responder `5xx` si no se puede persistir.
3. Implementar una máquina de estados de `EstadoPago` monotónica y bloquear la fila de pago/venta con `FOR UPDATE` en una sola transacción.
4. Antes de devolver un checkout existente, validar que la venta siga `PENDIENTE` y no expirada; cancelar/cerrar la sesión al cancelar la venta.
5. Resolver fan-out MP por todos los tokens/negocios; no retornar éxito ante falta de token o error de API.
6. Añadir frescura de firma MP y deduplicación por `request-id`/evento.
7. Resolver Stripe por tenant/secreto sin escanear todos los secretos; cachear con invalidación.
8. Pruebas de replay, eventos fuera de orden, dos tenants, cola caída, reinicio y fallo de API.

**Criterio de aceptación**: un evento aceptado con `200` está persistido; un pago aprobado no puede volver a pendiente/rechazado; una venta cancelada no puede cobrarse por replay; dos tenants no se bloquean entre sí.

**Rollback**: inbox y estados nuevos son aditivos; si se desactiva el procesamiento durable, conservar la tabla y reprocesar, no borrar evidencia.

### Fase 3 — Catálogo, stock e idempotencia (P1)

**Hallazgos**: `AL-05`, `AL-08`, `AL-09`.

1. Filtrar `activo=1` en catálogo público, búsqueda por ID y compra; método administrativo separado para inactivos.
2. Añadir regla de dominio `producto.puedeVenderse()` y aplicarla en carrito, compra directa y actualización de stock.
3. Diseñar reserva de stock con TTL: `stock_disponible`, `stock_reservado`, `reserva_expira_en`; job de liberación y compensación ante fallo de pasarela.
4. Migrar idempotencia a `UNIQUE (id_negocio, clave_idempotencia)` con verificación previa de duplicados; ajustar consulta y tests cross-tenant.
5. Pruebas de producto inactivo, concurrencia de stock, expiración y misma clave en dos tenants.

**Criterio de aceptación**: productos inactivos no se listan ni se venden; una venta fallida/abandonada libera stock al vencer; la misma clave en dos negocios funciona de forma independiente.

**Rollback**: la reserva puede desactivarse volviendo al descuento directo, pero primero liberar reservas pendientes; la migración de idempotencia requiere backup y plan de reversión de índice.

### Fase 4 — Sesiones y tenant (P1)

**Hallazgos**: `AL-06`, `AL-07`.

1. Añadir `token_version`/`sesion_version` por cuenta y validarla en cada request; incrementar al cambiar contraseña, rol, estado o vínculos.
2. Verificar negocio activo y vínculo activo en el filtro de autenticación, con caché de TTL corto e invalidación explícita.
3. Añadir TTL/invalidación al caché de `ResolutorNegocio`; invalidar al actualizar estado del negocio.
4. Reducir TTL del access token o introducir refresh token.
5. Pruebas de baja de cuenta, baja de vínculo, cambio de rol, negocio desactivado y token robado.

**Criterio de aceptación**: una baja o degradación invalida el acceso en el siguiente request; un negocio desactivado deja de resolver sin reiniciar.

**Rollback**: `token_version` es aditiva; si se despliega gradualmente, aceptar tokens sin versión solo durante una ventana corta y monitorear.

### Fase 5 — Robustez de API y operación (P1/P2)

**Hallazgos**: `ME-04`, `ME-05`, `ME-06`, `ME-08`, `ME-09`, `ME-10`, `ME-12`, `BA-01`, `BA-02`, `BA-05`.

1. Rate limit distribuido o consciente de proxy; no expulsar ventanas activas; límites de memoria.
2. Unificar resolución de configuración y probar precedencias; documentar la fuente efectiva sin imprimir secretos.
3. Validar URLs Stripe con HTTPS/allowlist y monto total; alinear con MP.
4. Reordenar actualización de venta/detalles o exigir transacción.
5. Excepciones de dominio y respuestas `404/409`; verificar filas afectadas.
6. Auditoría append-only generada por el sistema; corregir consulta por producto; añadir `id_negocio` o alcance.
7. Timeouts/límites en el servidor HTTP o proxy; prueba de carga.
8. Rollback con excepción suprimida; arranque con logging claro.
9. Pruebas de configuración, errores, rate limit y carga.

**Criterio de aceptación**: no hay `500` por recurso inexistente; la configuración efectiva es única y probada; el servidor resiste clientes lentos; la auditoría no es manipulable por endpoint.

### Fase 6 — Frontend y pruebas (P1/P2)

**Hallazgos**: `BA-04`, `ME-13`.

1. Escapar `log.descripcion` con `escaparHtml`/`textContent`; añadir CSP.
2. Convertir los tests Stripe vacíos en pruebas reales de firma, tenant, replay y fallo de API.
3. Añadir E2E de registro, compra, pago sandbox, cancelación y webhook.
4. Añadir pruebas de contrato HTTP para códigos `400/401/403/404/409/415/429/500`.

## Matriz de aceptación por hallazgo

| ID | Fix mínimo verificable |
|---|---|
| CR-01 | Registro/vínculo exige prueba de titularidad; no hay takeover por DNI/ID. |
| CR-02 | Registro admin no emite sesión para cuenta existente; invitación/consentimiento. |
| AL-01 | Checkout existente solo se devuelve si la venta está pendiente y vigente. |
| AL-02 | Transiciones de pago monotónicas y fila bloqueada; sin estados divergentes. |
| AL-03 | Evento persistido antes del `200`; reintentos y dead-letter. |
| AL-04 | Fan-out itera todos los tenants; fallo no se convierte en éxito. |
| AL-05 | Catálogo/compra solo con `activo=1`. |
| AL-06 | Baja/rol/negocio se reflejan en el siguiente request. |
| AL-07 | Caché de tenant con TTL/invalidación. |
| AL-08 | Reserva con expiración y compensación. |
| AL-09 | Unicidad de idempotencia por negocio. |
| ME-01 | Registro transaccional y respuesta no enumerable. |
| ME-02 | Bootstrap de un solo uso/expiración. |
| ME-03 | Error JSON serializado con `JsonUtils`/Jackson. |
| ME-04 | Rate limit no evadible por claves/proxy. |
| ME-05 | Una sola precedencia documentada y probada. |
| ME-06 | URL Stripe HTTPS/allowlist y monto máximo. |
| ME-07 | Ventana de frescura y deduplicación MP. |
| ME-08 | Detalles no se borran antes de un UPDATE exitoso. |
| ME-09 | `404/409` de dominio; sin borrados silenciosos. |
| ME-10 | Auditoría no manipulable; consulta por producto correcta. |
| ME-11 | Stripe valida por tenant sin escanear todos los secretos. |
| ME-12 | Timeouts/límites y prueba de carga. |
| ME-13 | Stripe/E2E/negativos cubiertos; archivos vacíos resueltos. |
| BA-01 | Rollback preserva excepción original. |
| BA-02 | Fallo de arranque claro y accionable. |
| BA-03 | Parámetros de hash y tope de iteraciones. |
| BA-04 | Dashboard escapa la descripción. |
| BA-05 | Repositorios verifican filas afectadas. |
| BA-06 | Documentación/artefactos limpios; secreto local fuera de Git. |

## Esfuerzo orientativo

| Bloque | Esfuerzo | Riesgo de cambio |
|---|---|---|
| Identidad (P0) | M | Alto: toca registro, vínculos y sesiones. |
| Pagos/webhooks (P0) | L | Alto: toca dinero y proveedores. |
| Catálogo/stock/idempotencia (P1) | L | Alto: esquema y concurrencia. |
| Sesiones/tenant (P1) | M | Medio: afecta todos los endpoints. |
| API/operación (P2) | M | Medio: contratos y despliegue. |
| Frontend/pruebas (P1/P2) | M | Bajo/medio. |

## Validación final

- `mvn test` completo en verde, incluyendo tests nuevos.
- Pruebas E2E en sandbox de MP y Stripe.
- Migraciones probadas en una copia de la base y con rollback documentado.
- Revisión adversarial independiente de los fixes.
- Actualización de `MEMORY.md` solo con decisiones aprobadas.
- Nuevo veredicto: `APROBADO CON RIESGOS` solo si quedan medios/bajos aceptados y documentados; `APROBADO` solo si no quedan P0/P1 y las pruebas E2E son concluyentes.
