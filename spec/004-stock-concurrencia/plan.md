# PLAN — Concurrencia e integridad del stock en la venta

## 1. Alcance e Impacto

- **Especificación:** `spec/004-stock-concurrencia/spec.md` y este plan.
- **Impacto:** `CRITICAL` — afecta dinero/stock, el flujo de creación de venta
  (`POST /api/ventas/crear`) y la consistencia de `productos`.
- **Dentro de alcance:** relectura bloqueante dentro de la transacción,
  verificación de filas afectadas, mapeo de stock insuficiente a `409`,
  ruta sin transacción segura, corrección del mapeo de `stockReservado`.
- **Fuera de alcance:** atomicidad de `cancelarVentaAsync` (P1), optimización
  del fan-out de webhooks, rediseño de reservas, cambios de esquema,
  reposición automática salvo decisión D-02.

## 2. Arquitectura y Componentes

- **CapaAplicacion — `VentaService`:**
  - `validarYActualizarStock(idNegocio, carrito, connection)` usa
    `productoRepository.buscarPorIdParaUpdate(idNegocio, id, connection)` por
    cada ítem, en orden ascendente de `idProducto` (RF-03), valida
    disponibilidad fresca y descuenta.
  - La validación previa contra el producto leído en el controller se
    elimina o queda solo como control temprano no definitivo.
  - Ruta `transaccion == null`: excepción explícita (RF-07 / D-04).
  - No cambia la firma de `crearVentaAsync`, `crearVentaConPagoAsync` ni
    `persistirVentaAtomica`.
- **CapaAplicacion — `IProductoRepository` / `ProductoService`:** sin cambios
  de contrato; `buscarPorIdParaUpdate` ya existe.
- **CapaInfraestructura — `MySqlRepositorioProducto`:**
  - `actualizar` verifica `executeUpdate() == 1` o lanza excepción (RF-04).
  - `map` sin cambios.
- **CapaDominio — `Producto`:** constructor asigna `stockReservado` (RF-09 /
  D-03). No se tocan `reducirStock`, `reponerStock`, `EstadoStock` (D-02).
- **CapaPresentacion — `BaseController`:** mapear
  `StockInsuficienteException` → `409` en `handleAsync.exceptionally`
  (RF-05/RF-06), junto a los mapeos existentes (`BaseController.java:146-181`).
- **Frontend:** sin cambios.

### Alternativas consideradas y motivos de rechazo

| Alternativa | Motivo de rechazo |
|---|---|
| `UPDATE` condicional atómico | Saltea `reducirStock`/reposición automática y desalinea `stockReservado`; el mensaje de stock insuficiente perdería contexto de dominio; requiere decidir D-02 antes |
| `synchronized`/lock en Java | No protege ante múltiples instancias del servidor; serializa ventas de negocios distintos |
| Nivel de aislamiento `SERIALIZABLE` | No evita lost updates por read-modify-write sin lock; aumenta deadlocks; cambio global de conexión |

## 3. Dependencias y Archivos

### Archivos a editar

- `src/com/CapaAplicacion/Service/VentaService.java`
- `src/com/CapaInfraestructura/Implementacion/MySqlRepositorioProducto.java`
- `src/com/CapaDominio/Entidades/Producto.java` (solo constructor, D-03)
- `src/com/CapaPresentacion/Controllers/BaseController.java` (mapeo 409)
- Tests existentes de `VentaService` que construyen sin transacción (ajuste
  por D-04).

### Archivos nuevos

- `test/java/com/CapaAplicacion/Service/VentaServiceConcurrenciaTest.java`
  (dobles con bloqueo determinista, sin MySQL real).

### Dependencias con otras especificaciones

- `spec/007-checkout-url-pago`: la venta se crea (y descuenta stock) al
  iniciar el checkout; 007 no cambia ese momento ni reintenta la creación con
  otra clave.
- `spec/005-credenciales-mercadopago`: independiente; ambas tocan rutas de
  pago pero sin solapamiento de archivos.
- Cancelación atómica (P1, sin spec aún): esta corrección no la incluye; su
  futura spec debe asumir el lock por producto introducido aquí.

## 4. Estrategia y Orden de Implementación

1. `MySqlRepositorioProducto.actualizar`: verificación de filas (RF-04).
2. `Producto`: constructor asigna `stockReservado` (RF-09, sujeto a D-03).
3. `VentaService.validarYActualizarStock`: relectura `FOR UPDATE` + orden de
   bloqueo + ruta sin transacción segura (RF-01..RF-03, RF-07).
4. `BaseController`: mapeo `StockInsuficienteException` → 409 (RF-05/RF-06).
5. Tests de concurrencia con dobles y barreras; ajuste de tests existentes.
6. Verificación: `mvn test` (suite afectada; completa si el tiempo lo permite)
   y revisión del diff.

### Riesgos de regresión

- Tests existentes que usan la ruta sin transacción pueden requerir ajuste
  (D-04); se documentará cada cambio en el test.
- El mapeo a 409 cambia la respuesta observable de stock insuficiente (de 500
  a 409); el frontend no depende del 500 (usa mensaje del servidor).
- La corrección del constructor puede alterar escrituras de
  `stock_reservado` en productos con valor distinto de 0; se verificará antes
  de implementar (D-03).

### Estrategia de pruebas

- **Unitarias de concurrencia (obligatorias, sin BD):** repositorio fake con
  un `ReentrantLock` por producto que serializa `buscarPorIdParaUpdate` y
  transacción fake con rollback; dos hilos con `CountDownLatch` reproduciendo
  el interleaving de la carrera; asserts sobre stock final y 409.
- **Unitarias de rollback:** ítem inválido/inventario insuficiente a mitad del
  carrito → stock intacto.
- **Aislamiento tenant:** producto de otro negocio → sin escritura.
- **Integración MySQL (opcional, solo con BD descartable autorizada por el
  usuario):** dos conexiones reales concurrentes; no se ejecutará por defecto
  en `mvn test`.

## 5. Aprobación

- **Estado:** `Aprobado por instrucción del usuario` (implementación
  solicitada el 2026-10-19; se adoptan las opciones recomendadas).
- **Decisiones bloqueantes:** D-01 a D-04 de `spec.md` §7 — resueltas.
- **Decisión del usuario y fecha:** "implementa cuando lo tengas armado",
  2026-10-19.
