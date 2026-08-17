# API_DOCUMENTATION.md — E-Commerce Backend (Java SE + HttpServer)

> **Documento definitivo de contrato API** para el frontend `frontend/` (`index.html`, `css/styles.css`, `js/api.js`, `js/app.js`).
> Consumidor objetivo: IA de desarrollo Frontend (Stitch). Fuente de verdad: código fuente Java del backend (`src/`).

---

## Índice

1. [Configuración Global](#1-configuración-global)
2. [Convenciones de Serialización](#2-convenciones-de-serialización)
3. [Enums del Dominio](#3-enums-del-dominio)
4. [Módulo Productos](#4-módulo-productos)
5. [Módulo Clientes](#5-módulo-clientes)
6. [Módulo Cuentas](#6-módulo-cuentas)
7. [Módulo Ventas](#7-módulo-ventas)
8. [Módulo Carrito](#8-módulo-carrito)
9. [Módulo Auditoría](#9-módulo-auditoría)
10. [Módulo Stock](#10-módulo-stock)
11. [Módulo Recomendaciones](#11-módulo-recomendaciones)
12. [Guía de Implementación Frontend — `js/api.js`](#12-guía-de-implementación-frontend--jsapijs)
13. [Guía de Implementación Frontend — `js/app.js`](#13-guía-de-implementación-frontend--jsappjs)
14. [Anexo: Mapa de Respuestas / Códigos HTTP](#14-anexo-mapa-de-respuestas--códigos-http)
15. [Advertencias y Caveats (Importante)](#15-advertencias-y-caveats-importante)

---

## 1. Configuración Global

| Propiedad | Valor |
|---|---|
| **Host / Puerto** | `http://localhost:8080` (definido en `CapaExterna.ServerMain`) |
| **Prefijo Base** | `/api` |
| **Base URL del Frontend** | `http://localhost:8080/api` |
| **Content-Type (Request)** | `application/json` |
| **Content-Type (Response)** | `application/json; charset=UTF-8` |
| **CORS** | `Access-Control-Allow-Origin: *`, métodos `GET, POST, PUT, DELETE, OPTIONS`, headers `Content-Type, Authorization` |
| **Preflight OPTIONS** | `204 No Content` (todos los controllers lo responden automáticamente) |
| **Autenticación** | No implementada. El header `Authorization` es permitido por CORS pero **no se valida** en el backend. |

**Cabeceras por defecto para toda llamada que envíe body:**

```http
Content-Type: application/json
Accept: application/json
```

**Nota de formato de números:** El servidor serializa `BigDecimal`/`double` como número JSON plano (p. ej. `1000.0`, `1500.50`). En el request se aceptan números.

---

## 2. Convenciones de Serialización

El backend usa `JsonUtils` (reflexión sobre campos privados / records), no Jackson. Esto produce:

| Tipo Java | Salida JSON |
|---|---|
| `Enum` | String con el **nombre exacto** en MAYÚSCULAS (`"PENDIENTE"`) |
| `LocalDate` | `"YYYY-MM-DD"` (ISO-8601, p. ej. `"1990-05-15"`) |
| `LocalDateTime` | `"YYYY-MM-DDTHH:mm:ss.SSS"` (p. ej. `"2026-08-14T10:30:00.123"`) |
| `BigDecimal` / `double` / `int` | número JSON plano |
| `boolean` | `true` / `false` |
| `List` / `Map` | arrays / objetos JSON |
| `Optional` | el contenido si presente, `null` si vacío |
| Body vacío (operaciones void) | respuesta **sin cuerpo** (status `200`/`201` con `Content-Length: 0`) |

### Formato de Error Estandarizado

Todas las respuestas de error usan un **objeto único**:

```json
{ "error": "<mensaje descriptivo>" }
```

### Mapa de Códigos de Estado (global, `BaseController`)

| Código | Cuándo |
|---|---|
| `200 OK` | GET/PUT/DELETE exitosos y operaciones de negocio (pago, cancelar, transferir, evaluar stock, recomendaciones) |
| `201 Created` | POST de creación exitoso (producto, cliente, cuenta, venta, auditoría) |
| `204 No Content` | Solo respuesta a preflight `OPTIONS` |
| `400 Bad Request` | `IllegalArgumentException` / `IllegalStateException` del servicio, ID inválido en ruta, parámetro requerido ausente, JSON inválido, enum inválido |
| `404 Not Found` | Recurso no encontrado (`Optional` vacío) o ruta inexistente |
| `405 Method Not Allowed` | Verbo HTTP no soportado en la ruta |
| `500 Internal Server Error` | `RuntimeException` genérica no mapeada (ver [Caveats](#15-advertencias-y-caveats-importante)) |

---

## 3. Enums del Dominio

Valores **exactos** (cadenas que llegan al frontend en los JSON):

### 3.1 `CategoriaProducto`

```
ELECTRONICA, ROPA, HOGAR, DEPORTES, LIBROS
```

### 3.2 `TipoDePago`

> Importante: el valor real del enum es `TARJETA_CREDITO` (NO `TARJETA` ni `TRANSFERENCIA`).

```
EFECTIVO, TARJETA_CREDITO
```

### 3.3 `EstadoVenta` — Máquina de Estados

```
PENDIENTE  ──►  PAGADO  ──►  ENVIADO      (ENVIADO es estado terminal)
PENDIENTE  ──►  PAGADO  ──►  CANCELADO    (CANCELADO es estado terminal)
```

Matriz de transiciones permitidas (`Venta.cambiarEstado`):

| Estado actual | Puede ir a |
|---|---|
| `PENDIENTE` | `PAGADO` |
| `PAGADO` | `ENVIADO`, `CANCELADO` |
| `ENVIADO` | *(ninguno)* |
| `CANCELADO` | *(ninguno)* |

**Toda transición no permitida lanza `IllegalStateException` → `400 Bad Request`.**

### 3.4 `EstadoStock` — Dos criterios DISTINTOS en producción

> **Discrepancia documentada:** el DTO `ProductoResponseDTO.estadoStock` y el endpoint `/api/stock/*` usan **umbrales diferentes**.

| Contexto | Evaluación | Umbral |
|---|---|---|
| **`ProductoResponseDTO.estadoStock`** (en `/api/productos*`) — `EstadoStock.evaluarEstado` | `stockFisico == 0` → `AGOTADO`; `1..10` → `CRITICO`; `>= 11` → `DISPONIBLE` | **10** |
| **`/api/stock/*`** — `StockService.evaluarStock` | `stockFisico == 0` → `AGOTADO`; `1..5` → `CRITICO`; `> 5` → `DISPONIBLE` | **5** |

Posibles valores: `DISPONIBLE`, `CRITICO`, `AGOTADO`, `EN_REPOSICION` (`EN_REPOSICION` no se emite por los endpoints actuales).

### 3.5 `AccionAuditoria`

| Valor del enum | `getDescripcion()` |
|---|---|
| `CREAR` | `PRODUCTO_CREADO` |
| `ACTUALIZAR` | `PRODUCTO_ACTUALIZADO` |
| `ELIMINAR` | `PRODUCTO_ELIMINADO` |
| `REGISTRAR_VENTA` | `VENTA_REGISTRADA` |
| `CANCELAR_VENTA` | `VENTA_CANCELADA` |

> Para `POST /api/auditoria` y `GET /api/auditoria/accion/{accion}` se envía/recibe el **nombre del enum** (ej. `"CREAR"`).

### 3.6 Tipos de Descuento (solo `POST /api/ventas/crear`)

`descuento.tipo` acepta (case-insensitive, se normaliza a MAYÚSCULAS): `PORCENTAJE`, `MONTO`, `ESPECIAL`. Cualquier otro valor se ignora (descuento queda `null`).

---

## 4. Módulo Productos

**Ruta base:** `/api/productos` · **Controller:** `ProductoController` · **Service:** `ProductoService`

### 4.1 `GET /api/productos`

- **Caso de uso:** Listar catálogo completo (página de inicio / vitrina).
- **Request:** sin body.
- **Response `200 OK`** — array de `ProductoResponseDTO`:

```json
[
  {
    "id": 1,
    "nombre": "Laptop Gamer",
    "precio": 1000.0,
    "stockFisico": 10,
    "stockReservado": 0,
    "stockDisponible": 10,
    "estadoStock": "DISPONIBLE",
    "categoria": "ELECTRONICA"
  }
]
```

### 4.2 `POST /api/productos`

- **Caso de uso:** Crear producto. Publica evento `ProductosEventos` (auditoría).
- **Request** — body `ProductoRequestDTO`:

```json
{
  "nombre": "Mouse Vertical",
  "precio": 50.0,
  "stock": 15,
  "categoria": "ELECTRONICA"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `nombre` | string | obligatorio, no vacío |
| `precio` | number | obligatorio, **> 0** |
| `stock` | int | obligatorio, **>= 0** |
| `categoria` | string enum | `ELECTRONICA | ROPA | HOGAR | DEPORTES | LIBROS` |

- **Response `201 Created`:** sin cuerpo.
- **Errores:** `400` con `{"error": ...}` (precio <= 0, categoría inválida, etc.).

### 4.3 `GET /api/productos/{id}`

- **Caso de uso:** Ficha de producto / detalle.
- **Response `200 OK`:** `ProductoResponseDTO` (ver 4.1).
- **Errores:** `404` `{"error":"Recurso no encontrado"}`; `400` si el ID no es numérico.

### 4.4 `PUT /api/productos/{id}`

- **Caso de uso:** Actualizar producto.
- **Request:** body `ProductoRequestDTO` (mismos campos que 4.2) + `{id}` en ruta.
- **Response `200 OK`:** sin cuerpo.
- **Errores:** `400` por validación; **`500`** si el producto no existe (ver Caveats).

### 4.5 `DELETE /api/productos/{id}`

- **Caso de uso:** Eliminar producto.
- **Response `200 OK`:** sin cuerpo.
- **Errores:** `400` si ID inválido; **no-op silencioso** si el ID no existe (NO devuelve `404`).

---

## 5. Módulo Clientes

**Ruta base:** `/api/clientes` · **Controller:** `ClienteController` · **Service:** `ClienteService`

### 5.1 `GET /api/clientes`

- **Response `200 OK`** — array de `ClienteResponseDTO`:

```json
[
  {
    "id": 1,
    "dni": "34567890",
    "cuit": "",
    "nombreCompleto": "Juan Perez",
    "direccion": "Av. Siempreviva 742",
    "fechaNacimiento": "1990-05-15",
    "localidad": "Springfield",
    "activo": true
  }
]
```

> **Caveat:** el campo `cuit` del request **NO se persiste** (el mapper no lo asigna). En las respuestas siempre será `""`. Los campos `nombre` y `apellido` no se exponen individualmente; vienen concatenados en `nombreCompleto`.

### 5.2 `POST /api/clientes`

- **Caso de uso:** Registro de cliente. Valida mayoría de edad (>= 18), DNI (7-8 dígitos), CUIT (11 dígitos, opcional) y caracteres de nombre/apellido.
- **Request** — body `ClienteRequestDTO`:

```json
{
  "dni": "34567890",
  "cuit": "20345678909",
  "nombre": "Juan",
  "apellido": "Perez",
  "direccion": "Av. Siempreviva 742",
  "fechaNacimiento": "1990-05-15",
  "localidad": "Springfield"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `dni` | string | obligatorio, 7-8 dígitos |
| `cuit` | string | opcional, exactamente 11 dígitos |
| `nombre` | string | obligatorio, solo letras/espacios |
| `apellido` | string | obligatorio, solo letras/espacios |
| `direccion` | string | obligatorio |
| `fechaNacimiento` | string `YYYY-MM-DD` | obligatorio, no futura, edad >= 18 |
| `localidad` | string | obligatorio |

- **Response `201 Created`:** sin cuerpo.
- **Errores:** `400` con el mensaje de la regla infringida (ej. `"El cliente debe ser mayor de 18 años para registrarse."`).

### 5.3 `GET /api/clientes/{id}`

- **Response `200 OK`:** `ClienteResponseDTO` | **`404`** si no existe.

### 5.4 `PUT /api/clientes/{id}`

- **Request:** body `ClienteRequestDTO` (igual que 5.2).
- **Response `200 OK`:** sin cuerpo. **`500`** si el cliente no existe.

### 5.5 `DELETE /api/clientes/{id}`

- **Response `200 OK`:** sin cuerpo. No-op silencioso si no existe.

---

## 6. Módulo Cuentas

**Ruta base:** `/api/cuentas` · **Controller:** `CuentaController` · **Service:** `CuentaService`

### 6.1 `GET /api/cuentas`

- **Response `200 OK`** — array de `CuentaResponseDTO`:

```json
[
  {
    "id": 1,
    "nombreCuenta": "jperez",
    "correoElectronico": "jperez@gmail.com",
    "rolUsuario": "CLIENTE",
    "token": "",
    "saldo": 5000.00
  }
]
```

> **Caveat:** el DTO de salida **NO incluye `idCliente`**. El `idCliente` solo se envía en el request (POST/PUT).

### 6.2 `POST /api/cuentas`

- **Caso de uso:** Crear cuenta de un cliente. La contraseña se ofusca (pepper) y hashea con BCrypt en el servidor (nunca devolver al cliente).
- **Request** — body `CuentaRequestDTO`:

```json
{
  "nombreCuenta": "jperez",
  "correoElectronico": "jperez@gmail.com",
  "contraseniaPlana": "Password123",
  "idCliente": 1,
  "rolUsuario": "CLIENTE",
  "saldo": 5000.00
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `nombreCuenta` | string | obligatorio, **sin espacios** |
| `correoElectronico` | string | obligatorio, contiene `@` y termina en `.com` |
| `contraseniaPlana` | string | obligatorio, >= 8 chars, al menos 1 mayúscula + 1 minúscula + 1 número |
| `idCliente` | int | obligatorio, > 0 |
| `rolUsuario` | string | obligatorio (ej. `"CLIENTE"`, `"ADMIN"`) |
| `saldo` | number | obligatorio, **>= 0** |

- **Response `201 Created`:** sin cuerpo.
- **Errores:** `400` (correo duplicado, contraseña débil, saldo negativo, etc.).

### 6.3 `GET /api/cuentas/{id}`

- **Response `200 OK`:** `CuentaResponseDTO` | **`404`** si no existe.

### 6.4 `PUT /api/cuentas/{id}`

- **Request:** body `CuentaRequestDTO` (igual que 6.2). Si `contraseniaPlana` viene vacío/`null`, se conserva la contraseña actual.
- **Response `200 OK`:** sin cuerpo.
- **Errores:** `400` (correo duplicado en otra cuenta, validación); `500` si la cuenta no existe.

### 6.5 `DELETE /api/cuentas/{id}`

- **Response `200 OK`:** sin cuerpo.

### 6.6 `POST /api/cuentas/transferir`

- **Caso de uso:** Transferencia atómica entre dos cuentas del cliente (debitar + acreditar).
- **Request** — body `TransferRequest` (record interno del controller):

```json
{
  "idCuentaOrigen": 1,
  "idCuentaDestino": 2,
  "monto": 1500.50
}
```

- **Response `200 OK`:** sin cuerpo.
- **Errores:** `400` si: `monto <= 0`, origen/destino no existen, `idCuentaOrigen == idCuentaDestino`, saldo insuficiente (`"Saldo insuficiente para realizar la extracción."`).

### 6.7 `GET /api/cuentas/buscarPorCorreo?correo=...`

- **Caso de uso:** Recuperar cuenta por correo electrónico (login / autocompletado).
- **Request:** query param `correo` (URL-encoded): `/api/cuentas/buscarPorCorreo?correo=jperez%40gmail.com`.
- **Response `200 OK`:** `CuentaResponseDTO` (un objeto, no array) | **`404`** si no existe.
- **Errores:** `400` `{"error":"Parámetro 'correo' es requerido."}` si falta el param.

---

## 7. Módulo Ventas

**Ruta base:** `/api/ventas` · **Controller:** `VentaController` · **Service:** `VentaService`

### 7.1 `POST /api/ventas/crear`

- **Caso de uso:** Crear y persistir una orden de compra. Valida stock disponible, descuenta stock físico (con reposición automática si llega a 0), publica evento `VentasEventos("CREADA", venta)`.
- **Request** — body `CreateVentaRequest` (records internos del controller):

```json
{
  "idCliente": 1,
  "tipoPago": "EFECTIVO",
  "descuento": {
    "tipo": "PORCENTAJE",
    "valor": 0.10
  },
  "items": [
    { "idProducto": 1, "cantidad": 2 },
    { "idProducto": 3, "cantidad": 1 }
  ]
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `idCliente` | int | obligatorio, > 0 |
| `tipoPago` | string enum | `EFECTIVO` \| `TARJETA_CREDITO` (case-insensitive) |
| `descuento` | object \| `null` | opcional. Si se omite, no hay descuento |
| `descuento.tipo` | string enum | `PORCENTAJE` \| `MONTO` \| `ESPECIAL` |
| `descuento.valor` | number | valor del descuento (para `ESPECIAL` se ignora) |
| `items` | array | **obligatorio**, al menos 1 elemento |
| `items[].idProducto` | int | debe existir en el catálogo |
| `items[].cantidad` | int | > 0 y <= `stockDisponible` |

- **Response `201 Created`:** sin cuerpo.
- **Errores de negocio `400`:**
  - `"La venta debe incluir al menos un ítem."` (items vacío/nulo)
  - `"Producto no encontrado con ID: X"` (producto inexistente)
  - `"No hay suficiente stock para el producto: X"` (stock insuficiente)
  - `"Stock insuficiente."` (validación interna del dominio)
  - enum `tipoPago` inválido (→ `IllegalArgumentException`, `400`)
  - `"El carrito no puede estar vacío para crear una venta."`

### 7.2 `GET /api/ventas/{id}`

- **Caso de uso:** Consultar estado y detalle de una venta (seguimiento de pedido).
- **Response `200 OK`** — entidad `Venta` serializada (campos privados vía reflexión):

```json
{
  "id": 1,
  "idCliente": 1,
  "fechaCompra": "2026-08-14T10:30:00.123",
  "estadoCompra": "PENDIENTE",
  "tipoPago": "EFECTIVO",
  "detalles": [
    {
      "idProducto": 1,
      "precioUnitario": 950.0,
      "cantidad": 2
    }
  ]
}
```

> `precioUnitario` ya incluye el descuento aplicado por unidad (`subtotal / cantidad`). El total se calcula como `Σ(precioUnitario * cantidad)`.

- **Errores:** `404` `{"error":"Recurso no encontrado"}` si no existe; `400` si ID inválido.

### 7.3 `POST /api/ventas/{id}/pago`

- **Caso de uso:** Confirmar pago. El backend debita automáticamente de **la primera cuenta del cliente cuyo saldo cubra el total** (`RepositorioCuenta.buscarPorIdCliente`). Transición `PENDIENTE → PAGADO`. Publica evento `"PAGADA"`.
- **Request:** ruta `{id}`. Sin body.
- **Response `200 OK`:** sin cuerpo.
- **Errores:**
  - `400` si la venta no está `PENDIENTE` (`"La venta no se encuentra en estado PENDIENTE."`) o no tiene detalles.
  - `500` (ver Caveats) si: venta no encontrada, el cliente no tiene cuentas, saldo insuficiente en todas las cuentas.

### 7.4 `POST /api/ventas/{id}/cancelar`

- **Caso de uso:** Cancelar una venta. Solo permitido desde `PENDIENTE` (transición `PENDIENTE → CANCELADO` no existe en la máquina de estados; ver caveat). Reintegra stock de cada producto del detalle. Publica evento `"CANCELADA"`.
- **Request:** ruta `{id}`. Sin body.
- **Response `200 OK`:** sin cuerpo.
- **Errores:**
  - `400` `"Transición inválida: no se puede pasar de X a CANCELADO."`
  - `500` (ver Caveats) si venta/producto no encontrado.

---

## 8. Módulo Carrito

**No existe un controller REST de carrito.** El `Carrito` es un concepto **transitorio del dominio** (`Carrito`, `ItemCarrito`) que se construye del lado del servidor en el momento de `POST /api/ventas/crear` y se descarta tras persistir la venta.

**Implicación para el frontend:** el carrito debe implementarse 100% en el cliente (`js/app.js`, estado en memoria / `localStorage`) y enviarse como parte del payload de `POST /api/ventas/crear`:

```json
{
  "idCliente": 1,
  "tipoPago": "TARJETA_CREDITO",
  "descuento": null,
  "items": [
    { "idProducto": 2, "cantidad": 1 }
  ]
}
```

Reglas de dominio relevantes para el UI del carrito:
- Cantidad por ítem debe ser `> 0`.
- Cantidad total por ítem no puede superar `stockDisponible` del producto (el backend rechaza con `400`).
- Si se agrega dos veces el mismo producto, el backend **suma** las cantidades (`Carrito.agregarItem`).

---

## 9. Módulo Auditoría

**Ruta base:** `/api/auditoria` · **Controller:** `AuditoriaController` · **Service:** `AuditoriaService`

### 9.1 `GET /api/auditoria`

- **Caso de uso:** Trazabilidad completa (historial de operaciones).
- **Response `200 OK`** — array de entidad `Auditoria`:

```json
[
  {
    "id": 1,
    "accion": "CREAR",
    "idEntidad": 3,
    "fecha": "2026-08-14T09:15:00.000",
    "descripcion": "Producto registrado"
  }
]
```

### 9.2 `POST /api/auditoria`

- **Caso de uso:** Registrar manualmente una operación de auditoría.
- **Request** — body `CrearAuditoriaRequest` (record del controller):

```json
{
  "accion": "CREAR",
  "idEntidad": 3,
  "descripcion": "Producto registrado"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `accion` | string enum | `CREAR` \| `ACTUALIZAR` \| `ELIMINAR` \| `REGISTRAR_VENTA` \| `CANCELAR_VENTA` (case-insensitive) |
| `idEntidad` | int | obligatorio, >= 0 |
| `descripcion` | string | obligatorio, no vacía |

- **Response `201 Created`:** sin cuerpo.
- **Errores:** `400` si falta `accion`/`descripcion` (`"La acción, el ID y la descripción son obligatorios."`), enum inválido, o descripción vacía.

### 9.3 `GET /api/auditoria/producto/{id}`

- **Caso de uso:** Filtrar auditoría por ID de entidad (producto/venta).
- **Response `200 OK`:** array de `Auditoria` (puede ser `[]`).
- **Errores:** `400` si ID inválido.

### 9.4 `GET /api/auditoria/accion/{accion}`

- **Caso de uso:** Filtrar auditoría por acción.
- **Request:** ruta con el **nombre del enum** en MAYÚSCULAS: `/api/auditoria/accion/CREAR`.
- **Response `200 OK`:** array de `Auditoria` (puede ser `[]`).
- **Errores:** `400` `{"error":"Parámetro de acción requerido."}` o enum inválido.

---

## 10. Módulo Stock

**Ruta base:** `/api/stock` · **Controller:** `StockController` · **Service:** `StockService`

### 10.1 `POST /api/stock/evaluar`

- **Caso de uso:** Evaluar el estado de stock de un producto (dispara alerta de reposición si está agotado).
- **Request** — body `EvaluarStockRequest`:

```json
{ "idProducto": 1 }
```

- **Response `200 OK`:** **string** con el nombre del enum `EstadoStock` (según `StockService`, umbral **5**):

```json
"DISPONIBLE"
```

Posibles respuestas: `"DISPONIBLE"`, `"CRITICO"`, `"AGOTADO"`.

- **Errores:** `404` `{"error":"Producto no encontrado."}`.

### 10.2 `GET /api/stock/{id}`

- **Caso de uso:** Consultar estado de stock vía GET.
- **Response `200 OK`:** mismo formato que 10.1 (string del enum).
- **Errores:** `404` `{"error":"Producto no encontrado."}`; `400` si ID inválido.

> **Nota de UI:** usa el umbral del DTO de producto (`estadoStock` en `/api/productos`, umbral 10) para las tarjetas de catálogo, y este endpoint (umbral 5) solo si se desea la evaluación de negocio.

---

## 11. Módulo Recomendaciones

**Ruta base:** `/api/recomendaciones` · **Controller:** `RecomendacionController` · **Service:** `RecomendacionService`

### 11.1 `POST /api/recomendaciones`

- **Caso de uso:** Recomendar productos de la **misma categoría** que el producto base, excluyendo al propio producto y a los sin stock disponible. Primero los que están dentro de `[precioMin, precioMax]` ("ideales"), luego los que exceden hasta un 5% del `precioMax` ("flexibles").
- **Request** — body `RecomendacionRequest`:

```json
{
  "idProductoActual": 1,
  "precioMin": 50.0,
  "precioMax": 500.0
}
```

- **Response `200 OK`** — array de entidad `Producto` (cada ítem con los **campos internos**, no el DTO):

```json
[
  {
    "id": 2,
    "nombre": "Mouse Vertical",
    "precio": 50.0,
    "stockFisico": 15,
    "stockReservado": 0,
    "categoria": "ELECTRONICA"
  }
]
```

> **Nota:** aquí NO vienen `stockDisponible` ni `estadoStock` (son getters, no campos serializables). Si el frontend los necesita, debe calcularlos: `stockDisponible = stockFisico - stockReservado`.

- **Errores:** `404` `{"error":"Producto base no encontrado."}`; `400` si payload inválido.

---

## 12. Guía de Implementación Frontend — `js/api.js`

**Arquitectura recomendada:** un helper central (`requestJson` para respuestas con body, `requestVoid` para respuestas vacías) + un objeto `api` con un sub-módulo por recurso. El helper debe lanzar/exponer el mensaje de `{"error": ...}` del backend.

### 12.1 Constantes y helpers

```js
export const BASE_URL = "http://localhost:8080/api";

const JSON_HEADERS = { "Content-Type": "application/json" };

async function requestJson(path, { method = "GET", body } = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: body ? JSON_HEADERS : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let payload = null;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = text; }
  }
  if (!res.ok) {
    const msg = payload?.error || text || `HTTP ${res.status}`;
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return payload; // object | array | string | null
}

async function requestVoid(path, { method = "POST", body } = {}) {
  await requestJson(path, { method, body }); // ignora el body vacío
  return true;
}
```

> Los endpoints de creación responden `201` y los de negocio `200`, todos **sin cuerpo**; usa `requestVoid` para ellos y no intentes `JSON.parse` del body vacío.

### 12.2 Prototipos de funciones por módulo

**Productos** (retornos: array / objeto `ProductoResponseDTO` / `true`)

```js
export async function listarProductos()            // GET  /productos        -> ProductoResponseDTO[]
export async function obtenerProducto(id)          // GET  /productos/{id}   -> ProductoResponseDTO | null
export async function crearProducto(payload)       // POST /productos        -> true
export async function actualizarProducto(id, payload) // PUT /productos/{id} -> true
export async function eliminarProducto(id)         // DELETE /productos/{id} -> true
```

**Clientes** (retornos: array / objeto `ClienteResponseDTO` / `true`)

```js
export async function listarClientes()             // GET  /clientes      -> ClienteResponseDTO[]
export async function obtenerCliente(id)           // GET  /clientes/{id} -> ClienteResponseDTO | null
export async function crearCliente(payload)        // POST /clientes      -> true
export async function actualizarCliente(id, payload) // PUT /clientes/{id}-> true
export async function eliminarCliente(id)          // DELETE /clientes/{id}-> true
```

**Cuentas** (retornos: array / objeto `CuentaResponseDTO` / `true`)

```js
export async function listarCuentas()              // GET  /cuentas -> CuentaResponseDTO[]
export async function obtenerCuenta(id)            // GET  /cuentas/{id} -> CuentaResponseDTO | null
export async function crearCuenta(payload)         // POST /cuentas -> true
export async function actualizarCuenta(id, payload)// PUT /cuentas/{id} -> true
export async function eliminarCuenta(id)           // DELETE /cuentas/{id} -> true
export async function transferir(payload)          // POST /cuentas/transferir -> true
export async function buscarCuentaPorCorreo(correo) // GET /cuentas/buscarPorCorreo?correo=... -> CuentaResponseDTO | null
```

**Ventas** (retornos: objeto `Venta` / `true`)

```js
export async function crearVenta(payload)          // POST /ventas/crear -> true
export async function obtenerVenta(id)             // GET  /ventas/{id} -> Venta | null
export async function pagarVenta(id)               // POST /ventas/{id}/pago -> true
export async function cancelarVenta(id)            // POST /ventas/{id}/cancelar -> true
```

**Auditoría** (retornos: array de `Auditoria` / `true`)

```js
export async function listarAuditoria()            // GET  /auditoria -> Auditoria[]
export async function crearAuditoria(payload)      // POST /auditoria -> true
export async function auditoriaPorProducto(id)     // GET  /auditoria/producto/{id} -> Auditoria[]
export async function auditoriaPorAccion(accion)   // GET  /auditoria/accion/{accion} -> Auditoria[]
```

**Stock** (retornos: string enum / `null`)

```js
export async function evaluarStock(idProducto)     // POST /stock/evaluar {idProducto} -> "DISPONIBLE" | "CRITICO" | "AGOTADO" | null
export async function obtenerStockPorId(id)        // GET  /stock/{id} -> string | null
```

**Recomendaciones** (retornos: array de `Producto`)

```js
export async function obtenerRecomendaciones(payload) // POST /recomendaciones -> Producto[]
```

### 12.3 Patrón de manejo de errores en la UI

```js
try {
  await api.ventas.create(payload);
  mostrarExito("Venta creada");
} catch (error) {
  mostrarError(error.message); // ej. "No hay suficiente stock para el producto: Laptop Gamer"
}
```

---

## 13. Guía de Implementación Frontend — `js/app.js`

### 13.1 Estado global sugerido

```js
const state = {
  productos: [],      // ProductoResponseDTO[]
  clientes: [],       // ClienteResponseDTO[]
  cuentas: [],        // CuentaResponseDTO[]
  ventaActual: null,  // Venta | null
  carrito: new Map(), // Map<idProducto, { idProducto, cantidad }>
  filtros: { categoria: "", estadoStock: "", busqueda: "" },
};
```

### 13.2 Máquina de estados de Venta (mapeo de `EstadoVenta`)

El backend controla las transiciones con la siguiente matriz (cualquier otra combinación → `400`):

| `estadoCompra` | Acciones UI habilitadas |
|---|---|
| `PENDIENTE` | `Pagar` (`POST /ventas/{id}/pago`), `Cancelar` (`POST /ventas/{id}/cancelar`) |
| `PAGADO` | `Marcar Enviado` (no hay endpoint; si la UI lo simula, usar el valor de estado) |
| `ENVIADO` | ninguna (terminal) |
| `CANCELADO` | ninguna (terminal) |

Recomendación de UI por estado:

```js
const VENTA_UI = {
  PENDIENTE: { label: "Pendiente", color: "warning", acciones: ["pagar", "cancelar"] },
  PAGADO:    { label: "Pagado",    color: "success", acciones: [] },
  ENVIADO:   { label: "Enviado",   color: "info",    acciones: [] },
  CANCELADO: { label: "Cancelado", color: "danger",  acciones: [] },
};
```

Regla de negocio visible para el usuario: **una venta solo se puede pagar/cancelar mientras esté `PENDIENTE`**. Después de `PAGADO`, solo `CANCELADO` es legal (pero no hay endpoint dedicado para cancelar pagadas; el endpoint `/cancelar` solo funciona desde `PENDIENTE`).

### 13.3 Reposición de stock y badges (mapeo de `EstadoStock`)

| `estadoStock` (de `/api/productos`) | Semántica | Umbral backend | Sugerencia visual |
|---|---|---|---|
| `DISPONIBLE` | stock saludable | `>= 11` | badge verde |
| `CRITICO` | quedan pocas unidades | `1..10` | badge ámbar + advertencia |
| `AGOTADO` | stock 0 | `== 0` | badge rojo + deshabilitar botón "Agregar al carrito" |

```js
function badgeStock(estado) {
  const clase = String(estado).toLowerCase(); // disponible | critico | agotado
  return `<span class="badge badge-${clase}">${estado}</span>`;
}
function stockHabilitado(producto) {
  return producto.estadoStock !== "AGOTADO" && (producto.stockDisponible ?? producto.stockFisico) > 0;
}
```

> **Regla de dominio:** al llegar el stock físico a `0`, el backend repone automáticamente (reponerStock) 100 uds si AGOTADO o 50 uds si CRITICO, según `EstadoStock.getCantidadAReponer`. La UI debe re-renderizar tras una venta/cancelación porque los `stockFisico`/`stockDisponible` cambian.

### 13.4 Mapeo de selectores y enums

- **Categoría de producto:** poblar el `<select>` con `["ELECTRONICA","ROPA","HOGAR","DEPORTES","LIBROS"]` y enviar el valor tal cual (MAYÚSCULAS).
- **Tipo de pago:** `["EFECTIVO","TARJETA_CREDITO"]`. No usar `TARJETA`/`TRANSFERENCIA` (no existen).
- **Acciones de auditoría:** `["CREAR","ACTUALIZAR","ELIMINAR","REGISTRAR_VENTA","CANCELAR_VENTA"]` (para `POST /api/auditoria` y filtros por acción).
- **Descuento:** tipo `["PORCENTAJE","MONTO","ESPECIAL"]`. Para `MONTO`, `valor` es importe en moneda; para `PORCENTAJE`, fracción (ej. `0.10` = 10%). Enviar `descuento: null` si no aplica.

### 13.5 Flujos críticos de UI

1. **Agregar al carrito → Crear venta:** construir el payload en el formato exacto del contrato 7.1 (idCliente, tipoPago, descuento|null, items). Validar antes `cantidad <= stockDisponible`.
2. **Pago:** llamar `pagarVenta(id)` y luego re-consultar `obtenerVenta(id)` (o re-listar) para refrescar el estado; mostrar `"PAGADO"`.
3. **Cancelación:** confirmación obligatoria en UI; tras éxito, refrescar producto(s) del detalle porque el stock se reintegra.
4. **Errores de negocio:** capturar `error.message` (extraído de `{"error": ...}`) y mostrarlo al usuario (ej. saldo insuficiente al pagar).

---

## 14. Anexo: Mapa de Respuestas / Códigos HTTP

| Endpoint | Éxito | Body Éxito | 400 | 404 | 500 |
|---|---|---|---|---|---|
| GET `/api/productos` | 200 | array DTO | – | – | – |
| POST `/api/productos` | 201 | vacío | validación | – | – |
| GET `/api/productos/{id}` | 200 | DTO | id inválido | no existe | – |
| PUT `/api/productos/{id}` | 200 | vacío | validación | – | no existe |
| DELETE `/api/productos/{id}` | 200 | vacío | id inválido | (no-op) | – |
| GET `/api/clientes` | 200 | array DTO | – | – | – |
| POST `/api/clientes` | 201 | vacío | validación | – | – |
| GET `/api/clientes/{id}` | 200 | DTO | id inválido | no existe | – |
| PUT `/api/clientes/{id}` | 200 | vacío | validación | – | no existe |
| DELETE `/api/clientes/{id}` | 200 | vacío | id inválido | (no-op) | – |
| GET `/api/cuentas` | 200 | array DTO | – | – | – |
| POST `/api/cuentas` | 201 | vacío | validación | – | – |
| GET `/api/cuentas/{id}` | 200 | DTO | id inválido | no existe | – |
| PUT `/api/cuentas/{id}` | 200 | vacío | validación | – | no existe |
| DELETE `/api/cuentas/{id}` | 200 | vacío | id inválido | – | – |
| POST `/api/cuentas/transferir` | 200 | vacío | monto/cuentas/saldo | – | – |
| GET `/api/cuentas/buscarPorCorreo` | 200 | DTO | param faltante | no existe | – |
| POST `/api/ventas/crear` | 201 | vacío | ítems/producto/stock/tipoPago | – | – |
| GET `/api/ventas/{id}` | 200 | Venta | id inválido | no existe | – |
| POST `/api/ventas/{id}/pago` | 200 | vacío | estado inválido | – | venta/cuentas/saldo |
| POST `/api/ventas/{id}/cancelar` | 200 | vacío | transición inválida | – | venta/producto |
| GET `/api/auditoria` | 200 | array | – | – | – |
| POST `/api/auditoria` | 201 | vacío | acción/descripción | – | – |
| GET `/api/auditoria/producto/{id}` | 200 | array | id inválido | – | – |
| GET `/api/auditoria/accion/{accion}` | 200 | array | acción inválida | – | – |
| POST `/api/stock/evaluar` | 200 | string enum | – | producto | – |
| GET `/api/stock/{id}` | 200 | string enum | id inválido | producto | – |
| POST `/api/recomendaciones` | 200 | array | payload | producto base | – |

**Convenciones generales:** todo error responde `{"error":"<mensaje>"}`. Los métodos no soportados en una ruta responden `405`.

---

## 15. Advertencias y Caveats (Importante)

1. **PUT/DELETE de recursos inexistentes → `500` (no `404`).** `ProductoService.actualizarAsync`, `ClienteService.actualizarAsync` y `CuentaService.actualizarAsync` lanzan `RuntimeException` que el `BaseController` mapea a `500`. **El frontend debe comprobar existencia (GET previo) o tratar `500` como recurso ausente.**
2. **DELETE silencioso:** eliminar un ID inexistente en productos/clientes/cuentas no produce error (no-op). La UI debe asumir éxito y refrescar.
3. **Errores de pago/cancelación de venta → `500`** (RuntimeException): venta no encontrada, cliente sin cuentas, saldo insuficiente. El `error.message` describe el problema; mostrarlo tal cual al usuario.
4. **`cuit` no se persiste** en clientes (mapper lo omite). Respuesta siempre `cuit: ""`.
5. **`CuentaResponseDTO` no incluye `idCliente`.** Para mostrar el vínculo cuenta-cliente hay que cruzar con el módulo de clientes.
6. **Dos umbrales de stock diferentes** (10 en DTO de producto vs 5 en `/api/stock`). Usar el del DTO para la vitrina.
7. **`TipoDePago` real es `TARJETA_CREDITO`**, no `TARJETA`.
8. **`POST /api/ventas/{id}/cancelar` solo funciona desde `PENDIENTE`** (la máquina de estados no permite `PAGADO → CANCELADO` en el endpoint actual). Una venta `PAGADO` no puede cancelarse por API.
9. **`detalles[].precioUnitario`** ya incluye descuento prorrateado (con redondeo a 2 decimales). El total es `Σ(precioUnitario * cantidad)`.
10. **Datos en memoria:** los repositorios son `static` in-memory; al reiniciar el servidor se pierde todo excepto los 3 productos precargados (`ServerMain.prepopulateData`).
11. **Serialización por reflexión:** los enums se envían como su nombre; `fechaCompra`/`fecha` como ISO `LocalDateTime`; no hay envoltorio `data:` en las respuestas.