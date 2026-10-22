# SPEC — Cancelación de venta atómica

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- `VentaService.cancelarVentaAsync` (`VentaService.java:392-428`) ejecuta una
  secuencia sin transacción:
  - lee la venta fuera de la transacción con `buscarPorId` (:398),
  - la cancela en memoria con `venta.Cancelar()` (:404),
  - por cada detalle lee el producto con `buscarPorId` sin bloqueo (:407),
    reintegra stock y llama `productoRepository.actualizar(...)` sin conexión
    (:413-414),
  - recién al final actualiza la venta, también sin conexión (:418), y publica
    el evento `CANCELADA` (:421).
- Consecuencias confirmadas:
  - **Doble reintegro por fallo parcial:** si el reintegro del producto 2 falla
    después de escribir el producto 1, la venta nunca queda CANCELADA en la
    base. Un reintento vuelve a leer la venta como PAGADO y reintegra **todos**
    los productos otra vez. El stock crece por encima del real.
  - **Doble reintegro por concurrencia:** dos cancelaciones simultáneas (doble
    clic, reintento del cliente) leen el mismo estado y reintegran dos veces.
  - **Lost update:** los productos se leen y escriben con
    `buscarPorId`/`actualizar` sin lock; compite con la creación de ventas
    corregida en `spec/004` (read-modify-write sin `FOR UPDATE`).
  - **Errores 500:** venta inexistente, transición inválida e incluso fallos de
    negocio se envuelven en `RuntimeException` (:423-425), por lo que el
    endpoint responde 500 en vez de 404/409.
  - **Evento inconsistente:** el evento se publica dentro de la misma secuencia
    que puede fallar; no queda atado al éxito de la persistencia.
- La venta se actualiza con `MySqlRepositorioVenta.persistirVenta`, que
  ejecuta el `UPDATE` sin verificar filas afectadas
  (`MySqlRepositorioVenta.java:169`).
- No existe lectura bloqueante de venta: `IVentaRepository` no tiene
  `buscarPorIdParaUpdate` y `MySqlRepositorioVenta` solo ofrece `buscarPorId`.
- `JDBCTransaccion` ya preserva `BusinessException` y hace rollback ante
  excepción (`JDBCTransaccion.java:10-29`, corregido en `spec/004`).
- Transiciones de dominio vigentes (`EstadoVenta.java:3-32`): PENDIENTE →
  PAGADO; PAGADO → ENVIADO | CANCELADO; ENVIADO y CANCELADO son terminales.
  Por lo tanto hoy solo una venta PAGADA puede cancelarse; el botón del
  frontend (`sales-list.js:60`) habilita cancelar para PENDIENTE/ENVIADO y lo
  deshabilita para PAGADO/CANCELADO, en contradicción con el dominio. Esa
  contradicción es un tema funcional aparte, fuera de esta corrección.

> Actualización (aprobada por el usuario, 2026-10-20): la regla vigente es
> PENDIENTE → PAGADO | CANCELADO; PAGADO → ENVIADO; PAGADO y ENVIADO no se
> cancelan; ENVIADO y CANCELADO son terminales. `sales-list.js` habilita
> Pagar/Cancelar solo en PENDIENTE y `Venta.rehidratarEstado` restaura el
> estado persistido sin ejecutar transiciones. Esto resuelve D-07.

### 1.2 Discrepancias con el informe previo

El informe describía "cancelación no atómica". La inspección confirma el
problema y agrega el efecto más grave: el reintento duplica el stock
reintegrado; además el endpoint devuelve 500 incluso para venta inexistente, y
la venta no tiene lock de fila ni verificación de actualización.

## 2. Objetivo

Que la cancelación de una venta sea todo-o-nada: la venta y el stock quedan
consistentes, el stock se reintegra exactamente una vez aunque haya fallos
parciales, reintentos o cancelaciones concurrentes, y los errores de negocio se
traducen en 404/409 sin filtrar detalles internos.

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: una venta CANCELADA reintegra el stock de sus detalles exactamente una
  vez.
- I-02: si la cancelación falla, no queda stock reintegrado ni venta modificada
  (rollback total).
- I-03: reintentos de cancelación sobre una venta ya CANCELADA no modifican el
  stock ni publican eventos.
- I-04: `stock_fisico >= 0` y `stock_reservado >= 0` se mantienen (invariantes
  de `spec/004`).
- I-05: ninguna cancelación puede tocar ventas o productos de otro negocio.

### 3.2 Requisitos funcionales

- RF-01: `cancelarVentaAsync` debe ejecutar toda la operación dentro de
  `transaccion.ejecutarTransaccion`.
- RF-02: dentro de la transacción, la venta debe releerse con
  `ventaRepository.buscarPorIdParaUpdate(idNegocio, idVenta, connection)`
  (nuevo método; en MySQL con `SELECT ... FOR UPDATE`).
- RF-03: venta inexistente en la relectura → `RecursoNoEncontradoException`
  (HTTP 404), sin escrituras.
- RF-04: venta ya CANCELADA → no-op idempotente con éxito (HTTP 200), sin
  reintegro, sin actualización y sin evento (D-02).
- RF-05: venta en estado no cancelable según el dominio (PAGADO, ENVIADO) →
  error de conflicto (HTTP 409) sin escrituras; no debe exponerse como 500
  (D-03). Actualización 2026-10-20: PENDIENTE sí es cancelable; ver D-07.
- RF-06: los productos del detalle deben releerse con
  `buscarPorIdParaUpdate(idNegocio, idProducto, connection)`, en orden
  ascendente de `idProducto` (mismo orden de bloqueo que la creación de ventas,
  `spec/004` RF-03), y escribirse con `actualizar(..., connection)`.
- RF-07: producto inexistente en la relectura → `RecursoNoEncontradoException`
  → rollback total → HTTP 404.
- RF-08: la venta debe actualizarse con `actualizar(..., connection)`;
  `MySqlRepositorioVenta.persistirVenta` debe verificar que el `UPDATE` afecta
  exactamente 1 fila y fallar en caso contrario (D-05).
- RF-09: ruta legacy sin transacción (`transaccion == null`) → fallar con
  excepción explícita, sin escrituras (D-06).
- RF-10: el evento `CANCELADA` se publica una sola vez y solo después de que la
  transacción haya hecho commit (D-04).
- RF-11: sin cambios en la firma pública `cancelarVentaAsync(long, int)` ni en
  el contrato HTTP (`POST /api/ventas/{id}/cancelar` → 200).
- RF-12: fuera de alcance: cambios en las transiciones de estado del dominio
  (permitir PENDIENTE/ENVIADO → CANCELADO), reembolsos, ajustes de UI de
  cancelación, notificaciones y cualquier cambio de esquema.

### 3.3 Requisitos no funcionales

- RNF-01: sin dependencias nuevas ni cambios de esquema.
- RNF-02: transacción corta: lock de la fila de venta y de los productos del
  detalle, sin bloquear otros negocios.
- RNF-03: los tests no dependen de MySQL real; reproducen fallos y carreras con
  dobles deterministas.
- RNF-04: se preservan contratos existentes de repositorios, servicios y
  códigos HTTP ya definidos (salvo los mapeos explícitos RF-03/RF-05).
- RNF-05: código y nombres en español, sin introducir patrones ajenos.

## 4. Casos de Uso

- Caso 1 (normal): venta PAGADA con 2 ítems → 200; stock reintegrado; venta
  CANCELADA; un evento `CANCELADA`.
- Caso 2 (límite): doble cancelación secuencial → la segunda responde 200 sin
  reintegro adicional ni evento.
- Caso 3 (concurrencia): dos cancelaciones simultáneas de la misma venta →
  exactamente un reintegro y un evento; stock final correcto.
- Caso 4 (error): venta con dos productos y el segundo ya no existe → rollback
  total: stock del primero intacto, venta sin cambios, sin evento; HTTP 404.
- Caso 5 (error): venta inexistente → HTTP 404.
- Caso 6 (error): venta PAGADO o ENVIADO → HTTP 409, sin escrituras.
- Caso 7 (concurrencia): cancelación y creación de venta del mismo producto
  simultáneas → sin deadlock y sin lost update (locks ascendentes).
- Caso 8 (límite): fallo de persistencia de la venta (0 filas) → rollback del
  stock y error, sin evento.

## 5. Criterios de Aceptación

- [ ] CA-01: dos cancelaciones (secuenciales o concurrentes) reintegran el
  stock exactamente una vez.
- [ ] CA-02: un fallo a mitad del reintegro revierte todo (stock y venta).
- [ ] CA-03: venta inexistente → 404 y estado no cancelable → 409; ninguno
  responde 500.
- [ ] CA-04: un reintento tras fallo reintegra exactamente una vez y deja la
  venta CANCELADA.
- [ ] CA-05: los productos se bloquean en orden ascendente de `idProducto`.
- [ ] CA-06: el evento `CANCELADA` se publica una sola vez y solo tras el
  commit.
- [ ] CA-07: la suite `mvn test` pasa; los tests nuevos no usan MySQL real.
- [ ] CA-08: no se introducen dependencias ni cambios de esquema y no cambian
  las transiciones de dominio.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- Sin cambios de esquema SQL ni migraciones.
- Sin nuevas dependencias.
- No cambiar reglas de dominio de `Producto`/`EstadoVenta` ni el mapeo de
  errores global existente.
- No tocar el flujo de creación de ventas ni los webhooks.
- No revertir archivos modificados del árbol de trabajo.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión explícita del usuario | Estado/fecha |
|----------|-------------------------|--------------------------------|--------------|
| D-01 Venta inexistente | (a) `RecursoNoEncontradoException` → 404; (b) 409; (c) mantener `RuntimeException` → 500 | Recomendada: (a), coherente con `spec/004` RF-06 | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Venta ya cancelada | (a) no-op idempotente 200; (b) 409 conflicto | Recomendada: (a), evita doble reintegro por doble clic/reintento | Aprobada por instrucción del usuario, 2026-10-19 |
| D-03 Estado no cancelable | (a) error de conflicto 409 sin escrituras; (b) 400/500 | Recomendada: (a), la operación no es válida en el estado actual | Aprobada por instrucción del usuario, 2026-10-19 |
| D-04 Momento del evento | (a) después del commit exitoso; (b) dentro de la transacción | Recomendada: (a), mismo patrón que la creación de ventas | Aprobada por instrucción del usuario, 2026-10-19 |
| D-05 Verificación de filas en el update de venta | (a) exigir exactamente 1 fila; (b) no verificar | Recomendada: (a), coherente con `spec/004` RF-04 | Aprobada por instrucción del usuario, 2026-10-19 |
| D-06 Ruta sin transacción | (a) `IllegalStateException` explícita; (b) mantener la ruta sin lock | Recomendada: (a), coherente con `spec/004` D-04 | Aprobada por instrucción del usuario, 2026-10-19 |
| D-07 Transiciones PENDIENTE/ENVIADO → CANCELADO y UI invertida | (a) preservar el dominio actual y documentar la discrepancia; (b) habilitarlas | Resuelta: se permite PENDIENTE → CANCELADO, se mantiene PAGADO no cancelable y se corrige la UI | Aprobada por el usuario, 2026-10-20 |
