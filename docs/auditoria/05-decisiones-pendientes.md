# Decisiones pendientes

## Estado

CR-01/CR-02 están implementados y documentados en `07-cierre-cr01-cr02.md`. D-04 y D-05 fueron aprobadas por el usuario el 2026-10-22 e implementadas en `spec/010-pagos-idempotencia-webhooks/`; el resto de las decisiones sigue pendiente de aprobación humana. Ninguna propuesta no aprobada debe registrarse en `MEMORY.md` como decisión.

## Decisiones que bloquean la remediación

### D-01 — Alcance de la primera remediación

**Opciones**:

- **A. Solo P0** (`CR-01`, `CR-02`, `AL-01` a `AL-04`): reduce el riesgo de toma de cuenta y pagos, pero deja productos inactivos, stock retenido, sesiones sin revocar e idempotencia global.
- **B. P0 + P1** (recomendado): incluye identidad, pagos, webhooks, catálogo/stock, idempotencia, sesiones y pruebas críticas. Es el mínimo para un go-live controlado.
- **C. Todo el backlog P0/P1/P2**: más limpio a largo plazo, pero retrasa la corrección de lo urgente y mezcla cambios de alto riesgo con higiene.

**Recomendación**: B, con entregas separadas por fase y una revisión adversarial al final de P0 y otra al final de P1.

### D-02 — Modelo de vinculación de clientes existentes

**Opciones**:

- **A. Invitación con token de un solo uso** enviada por email/SMS o entregada por el negocio (recomendado).
- **B. Verificación administrativa**: el admin aprueba la vinculación después de comprobar identidad documental.
- **C. Vincular solo si el cliente ya fue creado por ese usuario** (requiere crear el perfil desde la cuenta, no desde el backoffice).

**Recomendación**: A + aprobación administrativa para casos excepcionales. Nunca reactivar el vínculo automático por DNI.

**Impacto**: cambia el flujo de alta, la UX del cliente y la carga operativa. Requiere definir quién puede invitar y cuánto dura el token.

### D-03 — Registro de administradores con email existente

**Opciones**:

- **A. Prohibir el alta y exigir invitación/consentimiento del titular** (recomendado).
- **B. Permitir solo si el titular confirma por email antes de crear el vínculo**.
- **C. Mantener el comportamiento actual** (no aceptable: `CR-02`).

**Recomendación**: A, con notificación al titular y auditoría. Si se necesita alta asistida, usar invitación, no emisión de sesión.

### D-04 — Durabilidad de webhooks

**Opciones**:

- **A. Inbox/outbox en MySQL antes del `200`**, procesamiento con reintentos y dead-letter (recomendado).
- **B. Confiar en los reintentos del proveedor** y responder `5xx` cuando falle el procesamiento síncrono.
- **C. Cola externa (RabbitMQ/Kafka)** si ya existe infraestructura; hoy no está en el stack.

**Recomendación**: A. Es la opción con menor dependencia nueva y encaja con MySQL/transacciones existentes.

**Impacto**: nueva tabla, job de reintentos, estados de evento, retención y observabilidad.

### D-05 — Modelo de stock

**Opciones**:

- **A. Reserva con TTL + job de expiración** (recomendado): separa disponible/reservado, libera abandonos.
- **B. Descuento inmediato + compensación al fallar la pasarela**: más simple, pero no cubre abandono ni caída prolongada.
- **C. Cobro primero y descuento al confirmar pago**: evita retención, pero permite sobreventa si no hay reserva.

**Recomendación**: A. Requiere decidir TTL (por ejemplo, 15–30 minutos) y comportamiento al expirar una venta con pago tardío.

### D-06 — Migración de idempotencia

**Opciones**:

- **A. `UNIQUE (id_negocio, clave_idempotencia)`** con migración aditiva y backfill (recomendado).
- **B. Clave compuesta en aplicación** sin constraint (no recomendado: no protege concurrencia).
- **C. Mantener unicidad global** y generar claves con prefijo de negocio (mitiga colisiones, pero deja el esquema semánticamente incorrecto).

**Recomendación**: A. Antes de migrar, buscar duplicados y definir ventana de mantenimiento/rollback.

### D-07 — Estrategia de sesión y revocación

**Opciones**:

- **A. `token_version` por cuenta + TTL corto + refresh token** (recomendado).
- **B. Consulta a DB en cada request** (más simple, más carga).
- **C. Lista de revocación en Redis** (buena si Redis es obligatorio y está altamente disponible).

**Recomendación**: A, con verificación de negocio/vínculo activo en un filtro cacheado con TTL corto. Definir si se aceptan sesiones de 12 horas o se reducen.

### D-08 — Configuración y secretos

**Opciones**:

- **A. Una sola precedencia documentada (system > env > archivo) y un resolver único** (recomendado).
- **B. Mantener precedencias actuales y documentar cada excepción** (no recomendado: ya causó drift).
- **C. Migrar a un gestor de secretos externo** (ideal a mediano plazo; fuera del alcance inmediato si no existe).

**Recomendación**: A ahora, C como evolución. Rotar las credenciales DB que aparecen en `config.properties` local si alguna fue real; no imprimir valores.

### D-09 — Auditoría administrable

**Opciones**:

- **A. Eliminar el POST público/admin y generar eventos solo desde el sistema** (recomendado).
- **B. Mantenerlo solo para ADMIN con validación estricta, motivo y firma**.
- **C. Dejarlo como está** (no aceptable: `ME-10`).

**Recomendación**: A. Si se necesita una nota manual, crear un tipo de evento separado, inmutable y con autor explícito.

### D-10 — Limpieza y migraciones obsoletas

**Opciones**:

- **A. Mover migraciones obsoletas a `docs/legacy/` con aviso y eliminar archivos vacíos** (recomendado).
- **B. Borrar directamente** (riesgo de perder contexto histórico).
- **C. No hacer nada** (mantiene confusión operativa).

**Recomendación**: A, en un PR separado sin cambios funcionales.

## Información que debe confirmar el usuario

Estas respuestas cambian la explotación y la prioridad, no la existencia de los defectos:

1. **¿El sistema se usa realmente con múltiples negocios/tenants?** Si hoy es single-tenant, `CR-02`, `AL-04` y `AL-09` siguen siendo defectos de diseño, pero su urgencia operativa cambia. No se deben cerrar sin corregir.
2. **¿El registro público está habilitado en producción?** Si está deshabilitado, `CR-01/CR-02` requieren otro vector; el código sigue siendo inseguro.
3. **¿Hay pagos reales o solo sandbox?** Si no hay pagos reales, se puede priorizar identidad/catálogo, pero no omitir la integridad de pagos.
4. **¿Stripe está en uso?** Los tests están vacíos; si se usa, es un bloqueo adicional.
5. **¿Las credenciales de `config.properties` local fueron reales?** Si hay duda, rotarlas. No pegar valores en el chat.
6. **¿Qué ventana de mantenimiento y tolerancia de downtime hay para migraciones?**
7. **¿La reserva de stock es aceptable para el negocio?** Definir TTL y política de pago tardío.
8. **¿El frontend está en alcance de esta remediación?** `BA-04` es pequeño, pero requiere build/prueba de navegador.
9. **¿Se acepta un cambio de contrato para vinculación/invitaciones?** Impacta a clientes y operación.
10. **¿Quién aprueba el nuevo veredicto y con qué evidencia E2E?**

## Decisiones ya tomadas por evidencia (no requieren votación)

- No se debe desplegar en producción en el estado actual.
- No se debe implementar un fix sin prueba de regresión.
- No se debe usar la base ni credenciales de producción para tests.
- No se debe "arreglar" el problema de idempotencia solo en aplicación sin constraint de base.
- No se debe aceptar un webhook con `200` antes de persistirlo.
- No se debe reutilizar un checkout de una venta no pendiente.

## Registro de decisiones

| ID | Fecha | Decisión | Aprobado por | Evidencia |
|---|---|---|---|---|
| D-01 | pendiente | — | — | — |
| D-02 | pendiente | — | — | — |
| D-03 | pendiente | — | — | — |
| D-04 | 2026-10-22 | A: Outbox MySQL con procesamiento asíncrono, reintentos y dead-letter | Usuario | `spec/010-pagos-idempotencia-webhooks/`, `migracion_pagos_webhooks_expiracion.sql` |
| D-05 | 2026-10-22 | B adaptada: descuento inmediato de stock + expiración `PENDIENTE` con TTL 30 min y liberación de stock | Usuario | `spec/010-pagos-idempotencia-webhooks/`, `VentaService.expirarVentasPendientes` |
| D-06 | pendiente | — | — | — |
| D-07 | pendiente | — | — | — |
| D-08 | pendiente | — | — | — |
| D-09 | pendiente | — | — | — |
| D-10 | pendiente | — | — | — |

---

## Cierre final — Bloque 7

**Fecha:** 2026-10-24
**Veredicto:** **APROBADO** para el alcance de remediación P3 (BA-01, BA-03, BA-04, BA-05 y BA-06).

### Evidencia

- `mvn test`: **345 tests, 0 fallos, 0 errores, 1 omitido**; `BUILD SUCCESS`.
- Línea base: 216 tests; delta: +129 tests.
- BA-01: el fallo de `rollback()` se agrega como `Suppressed` a la excepción original.
- BA-03: PBKDF2 se mantiene en 210.000 iteraciones con comentario de hardening.
- BA-04: el resumen del Dashboard escapa acción, descripción y fecha con `escaparHtml`.
- BA-05: clientes y `Producto.eliminar` verifican filas afectadas y lanzan 404.
- BA-06: `classpath.txt` dejó de estar trackeado y fue ignorado; se eliminaron archivos vacíos y `app.jfr`.
- No se modificaron claves, secretos ni variables de entorno.
- Log detallado: `docs/auditoria/13-cierre-p3.md`.
- Roadmap: `posibles_mejores_futuro.md`.

### Condiciones operativas no bloqueantes

- No se ejecutó E2E real de Stripe/Mercado Pago; la cobertura sigue siendo con mocks o clientes falsos.
- Aplicar `migracion_auditoria_negocio.sql` en entornos donde `auditoria` todavía no tenga `id_negocio`.
- BA-02 se mantiene como fail-fast aceptado; agregar health check y logging estructurado en una iteración futura.

La línea de “no desplegar en producción” de la sección anterior corresponde al estado original auditado; este cierre documenta el estado posterior a P3.

**Actualización 2026-10-26:** la evidencia final con tests unitarios de
BA-01 a BA-06 y el escaneo de secretos está en
`docs/auditoria/15-cierre-bloque6-p3.md`.
