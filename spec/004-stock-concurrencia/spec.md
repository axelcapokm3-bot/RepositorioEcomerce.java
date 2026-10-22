# SPEC — Concurrencia e integridad del stock en la venta

## 1. Problema

### 1.1 Evidencia del comportamiento actual (inspección 2026-10-19)

- `VentaController.construirCarrito` lee los productos desde MySQL **fuera** de
  la transacción y construye el `ItemCarrito` con esos valores
  (`VentaController.java:187-204`). El stock leído puede quedar obsoleto antes
  de la transacción.
- `VentaService.persistirVentaAtomica` (`VentaService.java:149-170`) y
  `guardarVentaDeCheckout` (`VentaService.java:243-261`) abren transacción y
  llaman `validarYActualizarStock` dentro de ella.
- `VentaService.validarYActualizarStock` (`VentaService.java:320-348`) valida la
  disponibilidad contra el producto **en memoria**
  (`producto.getStockDisponible() < item.getCantidad()`, :330-333) y luego
  ejecuta `producto.reducirStock(...)` + `productoRepository.actualizar(...)`
  (:335-347) sin volver a leer la fila dentro de la transacción.
- `MySqlRepositorioProducto.actualizar` (`MySqlRepositorioProducto.java:128-144`)
  escribe la fila completa de forma incondicional
  (`SET stock_fisico = ?, stock_reservado = ? ... WHERE id = ? AND id_negocio = ?`)
  y no verifica filas afectadas. Dos transacciones concurrentes escriben el
  valor leído antes por cada una: la última pisa a la primera (lost update) y
  ambas pueden validar disponibilidad sobre el mismo stock (sobreventa).
- `MySqlRepositorioProducto.buscarPorIdParaUpdate`
  (`MySqlRepositorioProducto.java:91-101`) ya existe con `FOR UPDATE` y filtro
  por `id_negocio`, pero **no se usa en ningún flujo** (verificado por grep el
  2026-10-19: la única coincidencia es su definición).
- `JDBCTransaccion.ejecutarTransaccion` (`JDBCTransaccion.java:10-25`) abre
  conexión, `setAutoCommit(false)`, ejecuta, hace `commit` y ante excepción
  hace `rollback` envolviendo el error en `RuntimeException`. No fija nivel de
  aislamiento (queda el predeterminado de MySQL/InnoDB, `REPEATABLE READ`).
  `ServerMain` lo cablea como `ITransaccion` (`ServerMain.java:92`) y lo pasa a
  `VentaService` (`ServerMain.java:106-107`), por lo que la ruta transaccional
  es la de producción.
- Hallazgos colaterales confirmados en el dominio:
  - El constructor de `Producto` **descarta** el `stockReservado` recibido y lo
    fija en `0` (`Producto.java:42`), aunque `MySqlRepositorioProducto.map` lo
    lee de la base (`MySqlRepositorioProducto.java:173-181`). Con la escritura
    completa de `actualizar`, el valor de `stock_reservado` de la base se pisa
    con `0`.
  - `reservarStock` y `liberarReserva` (`Producto.java:128-140`) no se usan en
    ningún flujo (grep 2026-10-19).
  - `reducirStock` (`Producto.java:147-159`) valida contra `stockFisico`
    (`validarDisponibilidad`, :184-186) mientras el servicio valida contra
    `stockDisponible` (`stockFisico - stockReservado`, :122-124); además
    descuenta `stockReservado` si alcanza y **siempre** descuenta
    `stockFisico`, y al quedar en `0` llama a `reponerStock()`.
  - `reponerStock` (:161-171) con `stockFisico == 0` evalúa
    `EstadoStock.AGOTADO`, que repone **100 unidades**
    (`EstadoStock.java:5-27`): vender la última unidad reabastece stock
    automáticamente.

### 1.2 Discrepancias con el informe previo

El informe describía solo "validación en memoria + escritura". La inspección
confirma el problema y agrega: (a) la pérdida de `stock_reservado` al mapear,
(b) la doble semántica de `reducirStock`, y (c) la reposición automática de
100 unidades al llegar a 0. Estas tres particularidades condicionan la
estrategia de corrección y quedan como decisiones explícitas (§7).

## 2. Objetivo

Que dos ventas concurrentes del mismo negocio no puedan consumir más stock del
disponible, que una operación fallida no deje el stock parcialmente modificado
y que la venta, su detalle y el stock queden consistentes, preservando el
aislamiento por negocio y el modelo de stock existente
(`stockFisico` / `stockReservado`).

## 3. Requisitos y Reglas de Negocio

### 3.1 Invariantes

- I-01: `stock_fisico >= 0` y `stock_reservado >= 0` en `productos` para todo
  negocio y momento.
- I-02: la disponibilidad es `stock_fisico - stock_reservado` y nunca puede
  resultar negativa como consecuencia de una venta.
- I-03: una venta creada con éxito descuenta stock exactamente una vez; un
  fallo no descuenta nada.
- I-04: la suma de los descuentos aplicados equivale a la suma de los detalles
  de las ventas creadas (sin cancelaciones).
- I-05: ninguna operación de stock puede tocar productos de otro negocio.

### 3.2 Requisitos funcionales

- RF-01: dentro de la transacción existente, `validarYActualizarStock` debe
  releer cada producto con `buscarPorIdParaUpdate(idNegocio, id, connection)`
  antes de validar y escribir.
- RF-02: la validación de disponibilidad debe hacerse con los valores frescos
  devueltos por la relectura (`getStockDisponible() >= cantidad`) y con las
  mismas reglas de dominio vigentes.
- RF-03: cuando un carrito tenga más de un producto, los bloqueos deben
  adquirirse en orden ascendente de `idProducto` para evitar deadlocks.
- RF-04: `MySqlRepositorioProducto.actualizar` debe verificar el resultado del
  SQL y fallar (excepción) si `executeUpdate()` afecta a un número de filas
  distinto de 1.
- RF-05: stock insuficiente confirmado dentro de la transacción debe
  traducirse en `StockInsuficienteException`, rollback total y respuesta HTTP
  `409 Conflict` con el mensaje seguro existente del mapeo de errores (hoy
  `StockInsuficienteException` extiende `BusinessException` →
  `RuntimeException`, por lo que sin mapeo explícito el endpoint devuelve
  `500`; ver `BaseController.java:146-181`). No debe revelar datos internos.
- RF-06: producto inexistente o de otro negocio en la relectura debe
  considerarse error de negocio (sin escritura ni descuento) y responder
  `404 Not Found` mediante `RecursoNoEncontradoException` mapeada, no `500`
  (decisión de coherencia: el recurso solicitado ya no existe; stock
  insuficiente sigue siendo `409` por RF-05).
- RF-07: la ruta sin transacción (`transaccion == null`, constructores legacy
  usados por tests) no debe ejecutar la actualización de stock con
  read-modify-write sin lock: debe fallar con excepción explícita
  (`IllegalStateException`) si se intenta crear una venta por esa ruta.
- RF-08: la escritura de stock debe seguir scopeada por
  `WHERE id = ? AND id_negocio = ?` (sin cambios en el aislamiento).
- RF-09: corregir el constructor de `Producto` para que asigne el
  `stockReservado` recibido (condicionado a la decisión D-03 de §7), de modo
  que la relectura dentro de la transacción no lo pierda.
- RF-10: la cancelación de venta (`cancelarVentaAsync`) queda **fuera de
  alcance** de esta spec; su atomicidad se tratará en la corrección P1
  correspondiente. La presente corrección no debe empeorar ni alterar su
  comportamiento actual.

### 3.3 Requisitos no funcionales

- RNF-01: sin dependencias nuevas ni cambios de esquema.
- RNF-02: la transacción debe permanecer corta: locks solo sobre los productos
  del carrito, en una única transacción ya existente.
- RNF-03: los tests no pueden depender de MySQL compartido o de producción;
  deben poder reproducir la carrera con dobles de prueba deterministas.
- RNF-04: se preservan contratos públicos existentes de repositorios,
  servicios y códigos de error ya definidos (salvo el mapeo explícito de
  RF-05/RF-06).
- RNF-05: código y nombres en español en dominio/aplicación, sin introducir
  patrones ajenos al proyecto.

## 4. Casos de Uso

- Caso 1 (normal): venta de 1 producto con stock 10 y cantidad 2 → queda 8;
  venta y detalle persistidos; HTTP 201.
- Caso 2 (límite): dos ventas simultáneas de 1 unidad con stock 1 → una
  responde 201 y la otra 409; el stock final es 0 y nunca negativo.
- Caso 3 (límite): venta con cantidad igual al stock disponible → 201; la
  siguiente venta del mismo producto → 409.
- Caso 4 (error): stock insuficiente detectado en la relectura → rollback; sin
  venta, sin detalle, sin descuento; HTTP 409.
- Caso 5 (error): producto borrado o de otro negocio entre la lectura del
  request y la transacción → 409 sin efectos.
- Caso 6 (concurrencia): dos ventas concurrentes de dos productos compartidos
  en orden inverso → sin deadlock, gracias al orden de bloqueo (RF-03).
- Caso 7 (carrito multi-ítem): un ítem sin stock hace fallar la venta completa
  sin descontar los ítems válidos.

## 5. Criterios de Aceptación

- [ ] CA-01: dos ventas simultáneas no pueden consumir más stock del
  disponible (test de concurrencia con dobles y bloqueo determinista).
- [ ] CA-02: una operación fallida no deja el stock parcialmente modificado
  (verificación de rollback en test).
- [ ] CA-03: la actualización respeta `id_negocio`; un intento con producto de
  otro negocio no lo modifica.
- [ ] CA-04: se mantiene la consistencia entre venta, detalle y stock
  (stock final = inicial − suma de cantidades vendidas).
- [ ] CA-05: stock insuficiente en checkout devuelve `409` con mensaje seguro
  (no `500`).
- [ ] CA-06: la suite `mvn test` pasa completa; los tests nuevos de
  concurrencia se ejecutan sin base de datos real.
- [ ] CA-07: no se introducen dependencias ni cambios de esquema.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- Sin cambios de esquema SQL ni migraciones.
- Sin nuevas dependencias (ni Testcontainers, ni H2, ni librerías de
  concurrencia ajenas al JDK).
- No cambiar el modelo `stockFisico`/`stockReservado` ni las reglas de negocio
  existentes (incluida la reposición automática) sin decisión explícita.
- No tocar la confirmación de pagos ni el flujo de webhooks.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión explícita del usuario | Estado/fecha |
|----------|-------------------------|--------------------------------|--------------|
| D-01 Estrategia de concurrencia | (a) relectura con `buscarPorIdParaUpdate` (`FOR UPDATE`) dentro de la transacción y escritura desde dominio; (b) `UPDATE` condicional atómico (`stock_fisico = stock_fisico - ? ... AND disponible >= ?`) verificando filas; (c) sincronizar en Java | Recomendada: (a) — reutiliza infraestructura existente, conserva invariantes de dominio y la reposición automática, y mantiene el aislamiento por `id_negocio`; (b) se rechaza porque saltea las reglas de dominio y desalinea `stockReservado`/reposición | Aprobada por instrucción del usuario, 2026-10-19 |
| D-02 Reposición automática de 100 al llegar a 0 | (a) preservarla; (b) congelarla en esta corrección | Recomendada: (a) preservar (no contradecir el modelo actual); si se elige (b), debe registrarse como cambio funcional aparte | Aprobada por instrucción del usuario, 2026-10-19 |
| D-03 `stockReservado` descartado en `Producto` | (a) asignar el parámetro en el constructor; (b) no tocar y asumir que en producción siempre es 0 | Recomendada: (a); alternativa: verificar en la base (solo lectura) que `stock_reservado = 0` en todas las filas antes de elegir (b) | Aprobada por instrucción del usuario, 2026-10-19 |
| D-04 Ruta sin transacción | (a) fallar explícito si `transaccion == null`; (b) mantenerla con `UPDATE` condicional | Recomendada: (a), y ajustar los tests que hoy construyen `VentaService` sin transacción | Aprobada por instrucción del usuario, 2026-10-19 |
