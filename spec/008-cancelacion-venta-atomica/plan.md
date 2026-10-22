# PLAN — Cancelación de venta atómica

## 1. Alcance e Impacto

- **Especificación:** `spec/008-cancelacion-venta-atomica/spec.md` y este plan.
- **Impacto:** `CRITICAL` — afecta stock y ventas; endpoint
  `POST /api/ventas/{id}/cancelar`.
- **Dentro de alcance:** transacción única, lock de venta y productos,
  idempotencia de la cancelación, rollback total, mapeos 404/409,
  verificación de filas en el update de venta, evento post-commit y tests
  deterministas.
- **Fuera de alcance original:** transiciones nuevas (`PENDIENTE`/`ENVIADO` →
  `CANCELADO`), reembolsos, UI de cancelación, notificaciones, cambios de
  esquema. Actualización aprobada (2026-10-20): se habilitó únicamente
  `PENDIENTE → CANCELADO`, se prohibió `PAGADO → CANCELADO`, se corrigió la UI
  y se añadió rehidratación segura; ver `MEMORY.md`.

## 2. Arquitectura y Componentes

- **CapaAplicacion — `VentaService`:**
  - `cancelarVentaAsync` delega en un método privado `cancelarVentaAtomica`.
  - La operación completa corre dentro de `ejecutarTransaccion`.
  - Dentro de la transacción: relectura de venta con `FOR UPDATE`, no-op si ya
    está CANCELADA, validación de transición con `puedeTransicionarA`, reintegro
    de productos en orden ascendente de `idProducto` y actualización de la
    venta; todo con `Connection`.
  - La verificación de transición no escribe nada; si el estado no permite
    cancelar, la transacción termina sin cambios y el servicio lanza
    `IllegalStateException` **fuera** del wrapper para que
    `BaseController` responda 409 (sin modificar `JDBCTransaccion`, que envuelve
    las excepciones no `BusinessException`).
  - El evento `CANCELADA` se publica solo si hubo cancelación efectiva y la
    transacción retornó normalmente.
- **CapaAplicacion — `IVentaRepository`:** nuevo
  `default Optional<Venta> buscarPorIdParaUpdate(long, int, Connection) throws
  SQLException` que delega en `buscarPorId` (implementaciones en memoria sin
  cambios).
- **CapaInfraestructura — `MySqlRepositorioVenta`:**
  - `@Override buscarPorIdParaUpdate`: misma consulta que `buscarPorId` con
    `FOR UPDATE`.
  - `persistirVenta`: en la rama `update`, verificar
    `executeUpdate() == 1` o lanzar `SQLException`.
- **CapaPresentacion:** sin cambios; `BaseController` ya mapea
  `RecursoNoEncontradoException` → 404 e `IllegalStateException` → 409.
- **CapaDominio:** sin cambios (`EstadoVenta`, `Producto`).
- **Frontend:** sin cambios.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| Mantener la secuencia actual y solo envolverla en try/catch | No evita el doble reintegro parcial ni las carreras |
| `UPDATE` directo de stock sin relectura | Saltea las reglas de `Producto` y no protege lost updates |
| Marcar la venta CANCELADA primero y reintegrar después (dos transacciones) | Deja estados intermedios inconsistentes si falla el reintegro |
| Cambiar `JDBCTransaccion` para preservar `IllegalStateException` | Altera el comportamiento aprobado en `spec/004` (fallos de infraestructura pasarían a 409) |
| `synchronized` en Java | No protege ante múltiples instancias del servidor |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/com/CapaAplicacion/Interfaces/IVentaRepository.java`
- `src/com/CapaInfraestructura/Implementacion/MySqlRepositorioVenta.java`
- `src/com/CapaAplicacion/Service/VentaService.java`
- `test/java/com/CapaAplicacion/Service/VentaServiceTest.java` (el test de
  cancelación pasa a stubear `buscarPorIdParaUpdate` y la actualización con
  conexión; se agregan casos de 404/409/no-op/sin transacción).

### Archivos nuevos

- `test/java/com/CapaAplicacion/Service/VentaServiceCancelacionTest.java`
  (dobles en memoria con locks y rollback determinista; sin MySQL).

### Dependencias con otras especificaciones

- `spec/004-stock-concurrencia`: aporta el orden de bloqueo ascendente y la
  semántica de `buscarPorIdParaUpdate`; esta corrección reutiliza el patrón.
- `spec/007-checkout-url-pago`: una venta creada por checkout puede cancelarse;
  no se modifica el flujo de pago.
- `spec/005-credenciales-mercadopago`: independiente.

## 4. Estrategia y Orden de Implementación

1. `IVentaRepository.buscarPorIdParaUpdate` (default).
2. `MySqlRepositorioVenta`: consulta compartida + `FOR UPDATE` + verificación
   de filas en el update.
3. `VentaService.cancelarVentaAsync`: transacción, no-op idempotente,
   validación de estado, reintegro ordenado, evento post-commit y ruta sin
   transacción segura.
4. Actualización de `VentaServiceTest.cancelarVentaExitosa` y nuevos casos
   unitarios.
5. `VentaServiceCancelacionTest`: doble cancelación concurrente y rollback a
   mitad del detalle.
6. Verificación: `mvn -q test-compile` y suite dirigida
   (`VentaServiceTest`, `VentaServiceCancelacionTest`,
   `VentaServiceConcurrenciaTest`); luego suite unitaria sin
   `IdentidadGlobalMySqlTest`.

### Riesgos de regresión

- El no-op idempotente cambia la respuesta observable de una segunda
  cancelación (antes 500/409, ahora 200 sin efectos): es el comportamiento
  buscado (D-02).
- Los estados no cancelables pasan de 500 a 409: el frontend solo muestra el
  mensaje del servidor.
- El test existente de cancelación debe adaptarse al contrato transaccional
  (verificación con `Connection` y `buscarPorIdParaUpdate`).

### Estrategia de pruebas

- **Unitarias con Mockito:** venta inexistente (404), ya cancelada (no-op),
  estado PAGADO/ENVIADO (409), sin transacción, camino feliz con evento.
- **Dobles deterministas:** repositorio de venta con lock por venta,
  repositorio de producto con lock por producto y undo log por transacción;
  dos hilos con `CountDownLatch` para la doble cancelación; fallo de producto
  ausente para verificar rollback de stock y venta.
- **Regresión:** `VentaServiceTest` y `VentaServiceConcurrenciaTest` completos.
- **Integración MySQL:** no se ejecuta por defecto; el `FOR UPDATE` se valida
  por inspección y con el patrón ya probado en `spec/004`.

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (dirección elegida
  explícitamente en la sesión del 2026-10-19; se adoptan las opciones
  recomendadas).
- **Decisiones bloqueantes:** D-01 a D-06 resueltas; D-07 queda fuera de
  alcance.
- **Decisión del usuario y fecha:** "P1: cancelación atómica", 2026-10-19.
