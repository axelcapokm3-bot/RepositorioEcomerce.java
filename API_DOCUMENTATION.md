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
6. [Módulo Auth](#6-módulo-auth)
7. [Módulo Cuentas](#7-módulo-cuentas)
8. [Módulo Ventas](#8-módulo-ventas)
9. [Módulo Carrito](#9-módulo-carrito)
10. [Módulo Auditoría](#10-módulo-auditoría)
11. [Módulo Stock](#11-módulo-stock)
12. [Módulo Recomendaciones](#12-módulo-recomendaciones)
13. [Guía de Implementación Frontend — `js/api.js`](#13-guía-de-implementación-frontend--jsapijs)
14. [Guía de Implementación Frontend — `js/app.js`](#14-guía-de-implementación-frontend--jsappjs)
15. [Anexo: Mapa de Respuestas / Códigos HTTP](#15-anexo-mapa-de-respuestas--códigos-http)
16. [Advertencias y Caveats (Importante)](#16-advertencias-y-caveats-importante)

---

## 1. Configuración Global

| Propiedad | Valor |
|---|---|
| **Host / Puerto** | `http://localhost:8080` (definido en `CapaExterna.ServerMain`) |
| **Prefijo Base** | `/api` |
| **Base URL del Frontend** | `http://localhost:8080/api` |
| **Content-Type (Request)** | `application/json` |
| **Content-Type (Response)** | `application/json; charset=UTF-8` |
| **CORS** | Origen reflejado solo si está en la allowlist (`app.cors.allowed-origins`; si no se configura, `http://localhost:5500,http://127.0.0.1:5500,http://localhost:8080,http://127.0.0.1:8080`); métodos `GET, POST, PUT, DELETE, OPTIONS`, headers `Content-Type, Authorization, Idempotency-Key` |
| **Preflight OPTIONS** | `204 No Content` (todos los controllers lo responden automáticamente) |
| **Autenticación** | Bearer token HMAC-SHA256 en header `Authorization: Bearer <token>`. Obtenerlo en `POST /api/auth/registro` o `POST /api/auth/ingreso` (ver [Módulo Auth](#6-módulo-auth)). Sin token → `401`; sin rol suficiente → `403`. |
| **Roles** | `ADMIN` (gestión total) y `CLIENTE` (solo sus propios datos). Catálogo de productos en lectura y webhooks de pago son públicos; todo lo demás exige autenticación. |
| **Perfil prod** | Con `APP_PERFIL=prod` el servidor no inicia sin `AUTH_TOKEN_SECRET` (≥ 32 caracteres). Ver `docs/seguridad-operativa.md` para rotación, migración y bootstrap del primer ADMIN. |

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
{
  "error": "<mensaje descriptivo>",
  "codigo": 400,
  "timestamp": "2026-10-24T12:00:00Z"
}
```

`codigo` repite el status HTTP y `timestamp` es la fecha ISO-8601 UTC del error.
El campo `error` se conserva para compatibilidad con el frontend existente.

### Mapa de Códigos de Estado (global, `BaseController`)

| Código | Cuándo |
|---|---|
| `200 OK` | GET/PUT/DELETE exitosos y operaciones de negocio (pago, cancelar, transferir, evaluar stock, recomendaciones) |
| `201 Created` | POST de creación exitoso (producto, cliente, cuenta, venta, auditoría) |
| `204 No Content` | Preflight `OPTIONS` o `DELETE /api/carrito` exitoso |
| `400 Bad Request` | `IllegalArgumentException` del servicio, ID inválido en ruta, parámetro requerido ausente, JSON inválido, enum inválido |
| `401 Unauthorized` | Falta el header `Authorization` o el token es inválido/expirado |
| `403 Forbidden` | Token válido pero sin rol o propiedad suficiente sobre el recurso, o negocio inactivo/dado de baja |
| `404 Not Found` | Recurso no encontrado (`Optional` vacío) o ruta inexistente |
| `405 Method Not Allowed` | Verbo HTTP no soportado en la ruta |
| `409 Conflict` | Duplicidad de clave única, idempotencia, stock insuficiente, transición de estado inválida o regla de negocio |
| `429 Too Many Requests` | Se superó el límite configurable (`rate-limit.*`): login 10/min/IP y bloqueo de 60 s tras 5 intentos, registro 20/IP y 5 por correo cada 15 min, auth-cuenta 120, productos-admin/clientes-admin/ventas-consulta 120, ventas-previsualizar 60, ventas-crear 30, checkout 10/negocio y bloqueo de 60 s tras 20 intentos por IP, catálogo público 300/IP y 600/negocio, `carrito-consulta` 240/IP, 240/cuenta y 600/negocio, `carrito-modificacion` 120/IP, 120/cuenta y 300/negocio, y webhooks 600 por ventana; incluye `Retry-After` |
| `500 Internal Server Error` | `RuntimeException` genérica no mapeada (ver [Caveats](#15-advertencias-y-caveats-importante)) |

---

## 3. Enums del Dominio

Valores **exactos** (cadenas que llegan al frontend en los JSON):

### 3.1 `CategoriaProducto`

```
ELECTRONICA, ROPA, HOGAR_Y_MUEBLES, DEPORTES, INFORMATICA,
ALMACEN, HERRAMIENTAS, BEBIDAS, OTROS
```

`OTROS` es una constante de **solo lectura**: agrupa categorías de la base de datos que aún no tienen constante propia. Las respuestas de producto incluyen además `categoriaOriginal` con el nombre exacto almacenado en la tabla `categorias`. En escritura (`POST`/`PUT`) solo se aceptan categorías reales; `OTROS` se rechaza con `400 Bad Request`.

### 3.2 `TipoDePago`

> Importante: `TARJETA_CREDITO` existe en el enum, pero **no tiene pasarela implementada**; el checkout y la creación de ventas lo rechazan. Para cobros online usar `MERCADO_PAGO` o `STRIPE`; para cobro manual, `EFECTIVO` o `TRANSFERENCIA`.

```
EFECTIVO, TARJETA_CREDITO, TRANSFERENCIA, MERCADO_PAGO, STRIPE
```

### 3.3 `EstadoVenta` — Máquina de Estados

```
PENDIENTE  ──►  PAGADO  ──►  ENVIADO      (ENVIADO es estado terminal)
PENDIENTE  ──►  CANCELADO                 (CANCELADO es estado terminal)
PENDIENTE  ──►  EXPIRADO                  (EXPIRADO es estado terminal)
```

Matriz de transiciones permitidas (`Venta.cambiarEstado`):

| Estado actual | Puede ir a |
|---|---|
| `PENDIENTE` | `PAGADO`, `CANCELADO`, `EXPIRADO` |
| `PAGADO` | `ENVIADO` |
| `ENVIADO` | *(ninguno)* |
| `CANCELADO` | *(ninguno)* |
| `EXPIRADO` | *(ninguno)* |

`EXPIRADO` lo asigna el job de expiración cuando una venta `PENDIENTE` supera el
TTL configurado (`ventas.expiracion.minutos`, 30 por defecto). La transición
libera el stock y publica `"EXPIRADA"`; no se expone un endpoint público para
forzarla. Un pago aprobado tardío se conserva para conciliación sin reactivar la
venta.

**Toda transición no permitida lanza `IllegalStateException` → `409 Conflict`; en `POST /api/ventas/{id}/cancelar` el estado no cancelable también se traduce en `409 Conflict`.**

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
- **Response `200 OK`:** sin cuerpo. **`404`** si el cliente no existe o pertenece a otro negocio.

### 5.5 `DELETE /api/clientes/{id}`

- **Response `200 OK`:** sin cuerpo. **`404`** si el cliente no existe o pertenece a otro negocio; no se revela su existencia en otro tenant.

---

## 6. Módulo Auth

**Ruta base:** `/api/auth` · **Controller:** `AuthController` · **Service:** `AutenticacionService`

### 6.1 `POST /api/auth/registro`

- **Caso de uso:** Crear identidad global y vincularla al negocio del request con un perfil de cliente identificado por DNI. Si el DNI ya existe en el negocio, solo se vincula cuando `nombreCliente`, `apellidoCliente` y `fechaNacimientoCliente` coinciden con el titular registrado; los datos del cliente no se modifican. Si el DNI no existe, se crea el cliente en la tabla `clientes` con los datos enviados. La contraseña se hashea con PBKDF2-SHA256 (nunca se devuelve) y todo el alta se ejecuta en una única transacción.
- **Negocio:** se resuelve del header `X-Negocio-Subdominio` o del subdominio del host (negocio activo); el vínculo y el cliente nuevo pertenecen solo a ese negocio.
- **Autenticación:** no requerida. El campo `rol` solo acepta `ADMIN` si el solicitante es `ADMIN` del mismo negocio (enviar su token) o si se envía el header `X-Setup-Token` con el token de bootstrap del operador (`AUTH_BOOTSTRAP_TOKEN`); en cualquier otro caso la cuenta se crea como `CLIENTE`. Sin token de bootstrap configurado no existe registro público de administradores. El token de bootstrap es de un solo uso y se persiste como huella SHA-256 en `tokens_bootstrap`; su consumo ocurre dentro de la transacción de registro y vence según `auth.bootstrap-token-expira-horas` (24 h por defecto).
- **Correo existente:** no se duplica la cuenta y nunca se emite una sesión sin autenticar al titular. El request debe incluir la contraseña correcta de esa cuenta; si el solicitante está autenticado, la cuenta existente debe ser la suya. Un `ADMIN` no puede registrar ni vincular el correo de otra cuenta en su negocio: debe hacerlo el titular desde su propia sesión (`POST /api/auth/vincular`).
- **Request:**

```json
{
  "nombreCuenta": "jperez",
  "correoElectronico": "jperez@gmail.com",
  "contraseniaPlana": "Password123",
  "dniCliente": "38456789",
  "rol": "CLIENTE",
  "nombreCliente": "Juan",
  "apellidoCliente": "Pérez",
  "fechaNacimientoCliente": "1990-05-15",
  "direccionCliente": "Av. Siempre Viva 742",
  "localidadCliente": "Springfield"
}
```

| Campo | Regla |
|---|---|
| `nombreCuenta` | obligatorio, sin espacios, ≤ 100 chars |
| `correoElectronico` | obligatorio, formato válido, único |
| `contraseniaPlana` | ≥ 8 chars, 1 mayúscula + 1 minúscula + 1 número |
| `dniCliente` | obligatorio, 7 u 8 dígitos |
| `rol` | opcional (`CLIENTE` por defecto) |
| `nombreCliente`, `apellidoCliente`, `fechaNacimientoCliente` | obligatorios siempre: si el DNI ya existe deben coincidir con el titular registrado (comparación normalizada); si el DNI es nuevo crean el cliente |
| `direccionCliente`, `localidadCliente` | obligatorios solo si el DNI no existe en el negocio; se validan con las reglas de `ClienteRequestDTO` (letras y espacios, mayoría de 18 años, sin fechas futuras) |

- **Response `201 Created`:**

```json
{
  "token": "<base64url>.<base64url>",
  "rol": "CLIENTE",
  "idCuenta": 3,
  "idCliente": 1,
  "idNegocio": 1,
  "expiraEn": 1728576000
}
```

- **Errores:** `400` validación (incluye negocio no identificado, datos de cliente inválidos cuando el DNI es nuevo y credenciales incorrectas si el correo ya existe); `409` vínculo ya existente, perfil ya vinculado a otra cuenta, identidad que no coincide, negocio inactivo o intento de operar una cuenta ajena; `429` si se supera el límite configurable de registro por IP/cuenta.

### 6.2 `POST /api/auth/ingreso`

- **Caso de uso:** Ingresar con la identidad global en el negocio del request. El servidor verifica la contraseña, que la cuenta esté activa y que exista un vínculo activo con ese negocio; el token emitido lleva el `idNegocio`, `idCliente` y `rol` de ese vínculo.
- **Request:** `{ "correoElectronico": "...", "contraseniaPlana": "..." }`.
- **Response `200 OK`:** `SesionResponseDTO` (igual que 6.1).
- **Errores:** `400` credenciales inválidas (mensaje genérico, sin revelar si el correo existe) o negocio no identificado; `409` cuenta sin acceso a este negocio, cuenta o vínculo bloqueado/dado de baja; `429` por abuso.

### 6.2bis `POST /api/auth/negocio-activo`

- **Caso de uso:** Cambiar el negocio activo de la sesión (una misma identidad global accede a varios negocios). Requiere `Authorization: Bearer <token>`.
- **Negocio objetivo:** se resuelve del header `X-Negocio-Subdominio` o del subdominio del `Host`; si no se indica ninguno, se usa el negocio del JWT. El valor `idNegocio` del body se ignora y no forma parte del contrato. El servidor comprueba que la cuenta tenga un vínculo activo con ese negocio, re-resuelve su rol y emite un token nuevo.
- **Request:** sin body (cualquier `idNegocio` enviado en el body se descarta).
- **Response `200 OK`:** `SesionResponseDTO` del negocio solicitado.
- **Errores:** `401` token inválido; `400` subdominio inexistente o inactivo; `409` sin acceso al negocio solicitado o vínculo bloqueado/dado de baja; `429` por abuso.

### 6.2ter `GET /api/auth/negocios`

- **Caso de uso:** Listar los negocios con vínculo activo de la cuenta autenticada (para el selector de negocio activo). Omite vínculos dados de baja y negocios inactivos. Requiere `Authorization: Bearer <token>`.
- **Response `200 OK`:** `[{ "idNegocio": 1, "nombreNegocio": "Tienda Uno", "subdominio": "uno", "idCliente": 5, "rol": "CLIENTE", "estado": "ACTIVA" }, …]`.
- **Errores:** `401` token inválido.

### 6.2quater `POST /api/auth/vincular`

- **Caso de uso:** Vincular la cuenta autenticada a otro negocio con un perfil de cliente existente de ese negocio. El servidor resuelve el perfil por DNI y exige que nombre, apellido y fecha de nacimiento coincidan con el titular; no acepta un `idCliente` arbitrario. Requiere `Authorization: Bearer <token>`.
- **Negocio objetivo:** se resuelve del header `X-Negocio-Subdominio` o del subdominio del `Host`; si no se indica ninguno, se usa el negocio del JWT. Debe existir y estar activo. El valor `idNegocio` del body se ignora y no forma parte del contrato.
- **Request:** `{ "dniCliente": "30987654", "nombreCliente": "Ana", "apellidoCliente": "Gómez", "fechaNacimientoCliente": "1992-03-10", "contraseniaPlana": "…" }` (reconfirma la contraseña; el rol otorgado es siempre `CLIENTE`, nunca `ADMIN`).
- **Response `200 OK`:** `SesionResponseDTO` del negocio recién vinculado.
- **Errores:** `401` token inválido; `400` datos inválidos, subdominio inexistente/inactivo o credenciales incorrectas; `409` vínculo ya existente, identidad que no coincide, perfil ya vinculado a otra cuenta, negocio inactivo o perfil inexistente; `429` por abuso.

### 6.2quinquies `POST /api/auth/salida`

- **Caso de uso:** Revocar el token actual de forma inmediata. Requiere `Authorization: Bearer <token>`. El token queda registrado en `tokens_revocados` por su huella SHA-256 y deja de ser válido en el siguiente request, aunque no haya expirado.
- **Request:** sin body.
- **Response `200 OK`:** `{ "mensaje": "Sesión cerrada." }`.
- **Errores:** `401` token ausente/inválido/expirado; `429` por abuso.

### 6.3 `GET /api/auth/sesion`

- **Caso de uso:** Verificar el token actual. Requiere `Authorization: Bearer <token>`.
- **Response `200 OK`:** `{ "idCuenta": 3, "idCliente": 1, "idNegocio": 1, "rol": "CLIENTE" }` (vínculo activo de la sesión).
- **Errores:** `401` token ausente/inválido/expirado.

### 6.4 Matriz de autorización por endpoint

| Endpoint | Sin auth | CLIENTE | ADMIN |
|---|---|---|---|
| `POST /api/auth/*` | ✅ | ✅ | ✅ |
| `GET /api/productos*` | ✅ (lectura) | ✅ | ✅ |
| `POST/PUT/DELETE /api/productos*` | ❌ | ❌ | ✅ |
| `GET /api/clientes` | ❌ | ❌ | ✅ |
| `POST /api/clientes` | ❌ | ❌ | ✅ |
| `GET/PUT /api/clientes/{id}` | ❌ | propio `id` | ✅ |
| `DELETE /api/clientes/{id}` | ❌ | ❌ | ✅ |
| `POST /api/ventas/*`, `GET /api/ventas/{id}` | ❌ | propias | ✅ |
| `GET/POST /api/auditoria*` | ❌ | ❌ | ✅ |
| `GET/PUT/POST /api/negocios/*/configuraciones` | ❌ | ❌ | ✅ (secretos redactados como `***`) |
| `GET/POST /api/stock/*`, `POST /api/recomendaciones` | ❌ | ✅ | ✅ |
| `POST /api/webhooks/*` | ✅ (firma HMAC del proveedor) | n/a | n/a |

---

## 7. Módulo Cuentas

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

## 8. Módulo Ventas

**Ruta base:** `/api/ventas` · **Controller:** `VentaController` · **Service:** `VentaService`

### 7.1 `POST /api/ventas/crear`

- **Caso de uso:** Crear una venta y comenzar su checkout. Valida stock y persiste la venta antes de iniciar el pago. El checkout público admite `MERCADO_PAGO` o `STRIPE`.
- **Encabezado obligatorio:** `Idempotency-Key`, una clave estable de hasta 255 caracteres para reintentar esta misma solicitud.
- **Idempotencia:** la misma clave con cliente, tipo de pago, total y productos idénticos reutiliza la venta y el intento de pago ya creados. La misma clave con datos diferentes se rechaza. La venta y el descuento de inventario se guardan en una transacción.
- **Request** — body `CreateVentaRequest` (records internos del controller):

```json
{
  "idCliente": 1,
  "tipoPago": "MERCADO_PAGO",
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
| `tipoPago` | string enum | `MERCADO_PAGO` \| `STRIPE` (case-insensitive) |
| `descuento` | object \| `null` | opcional. Si se omite, no hay descuento |
| `descuento.tipo` | string enum | `PORCENTAJE` \| `MONTO` \| `ESPECIAL` |
| `descuento.valor` | number | valor del descuento (para `ESPECIAL` se ignora) |
| `items` | array | **obligatorio**, al menos 1 elemento |
| `items[].idProducto` | int | debe existir en el catálogo |
| `items[].cantidad` | int | > 0 y <= `stockDisponible` |

- **Response `201 Created`:** resultado del checkout, incluido su estado y `urlPago` cuando la pasarela devuelve una URL. Mercado Pago usa Orders API y devuelve su `checkout_url`; no crea una Preference.
- **Configuración local de Mercado Pago:** `MERCADOPAGO_PUBLIC_BASE_URL` debe ser la URL HTTPS pública del frontend alcanzable por ngrok hacia Caddy (`localhost:8081`). Con el Caddyfile local compartido, `/api/*` se reenvía a Java (`localhost:8080`) y el resto de las rutas a Live Server (`localhost:5500`), preservando la ruta. Por eso, si Live Server sirve `src/index.html` en `/src/index.html`, usa `https://<host-ngrok-actual>/src/index.html` como base de retorno. El hostname lo asigna ngrok y puede cambiar; no lo fijes en el repositorio. El backend deriva los retornos con `payment_status=success`, `payment_status=failure` y `payment_status=pending`; el `auto_return` queda en `approved`. Como alternativa, se admiten las propiedades `mercadopago.public-base-url` o `mercadopago.checkout-return-url`.
- **URL del webhook de Mercado Pago:** configurar en Mercado Pago `https://<host-ngrok-actual>/api/webhooks/mercado-pago`. La regla `@api path /api/*` de Caddy reenvía esa ruta al backend Java (`localhost:8080`); no usar la URL del frontend para notificaciones. La firma se valida con `MERCADOPAGO_WEBHOOK_SECRET`/`mercadopago.webhook-secret`, no con la URL. La URL del frontend recibe los retornos visibles del checkout; el webhook se procesa en el backend. El `ts` de la firma debe estar dentro de `webhook.firma-tolerancia-segundos` (300 s por defecto); fuera de esa ventana se rechaza con `401` para evitar replay.
- **Stripe:** configurar `stripe.secret-key`, `stripe.success-url` y `stripe.cancel-url`. La URL de éxito debe incluir `{CHECKOUT_SESSION_ID}`. `stripe.allowed-return-origins` (o, en su defecto, `app.frontend.url`) restringe los orígenes de retorno; con `ENTORNO=prod` o `APP_PERFIL=prod` la allowlist es obligatoria, las URLs deben ser HTTPS públicas y se rechazan localhost, IP privadas, credenciales embebidas y fragmentos. Los timeouts hacia Stripe se configuran con `stripe.connect-timeout-ms` (5000) y `stripe.read-timeout-ms` (5000). Los webhooks de Stripe se validan con la tolerancia de 300 s del SDK y se deduplican por `evento_id`.
- **Durabilidad de webhooks:** tras validar la firma, el backend persiste el evento en `eventos_webhook` antes de responder `200`. Si falla la persistencia responde `500` para que el proveedor reintente; si el cuerpo o el identificador son inválidos responde `400`. El procesamiento posterior es asíncrono, con reintentos y dead-letter (`webhook.outbox.*`). Un evento duplicado por `(proveedor, tipo, evento_id)` no se vuelve a registrar.
- **URL de API del frontend:** cuando se sirve detrás de Caddy (local o por ngrok), el navegador usa `/api` en el mismo origen para que Caddy aplique la regla de proxy. Al abrir Live Server directamente en `localhost:5500`, el cliente conserva el destino local `localhost:8080`.
- **Fuente de configuración:** `ConfigLoader` resuelve system property → variable de entorno → `config.properties` local, incluidos los secretos. `config.properties` es solo para desarrollo, está ignorado por Git y excluido del `.jar`. Con `ENTORNO=prod` (o `APP_PERFIL=prod` como fallback), el arranque falla si el archivo existe en el working directory. No guardar la URL temporal de ngrok en producción ni versionarla.
- **Errores de negocio `400`:** datos de entrada inválidos (`idCliente` no positivo, JSON malformado o enum inválido).
- **Errores de negocio `404`:** `"El producto ya no está disponible: X"` si el producto no existe, está inactivo o fue eliminado durante el checkout.
- **Errores de negocio `409`:**
  - `"No hay suficiente stock para el producto: X"` / `"Stock insuficiente."`.
  - `"El carrito no puede estar vacío para crear una venta."`.
  - `Idempotency-Key` ausente, reutilizada con otro contenido o tipo de pago inválido.
  - Duplicidad de clave única o estado de venta no compatible.
- **Error de negocio `403`:** `"El negocio no está disponible."` si el negocio se encuentra inactivo o dado de baja; la venta no se persiste.

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

- **Caso de uso:** Cancelar una venta. El dominio permite únicamente la transición `PENDIENTE → CANCELADO`; una venta `PAGADO` o `ENVIADO` no puede cancelarse. La operación es transaccional (venta + stock) e idempotente: cancelar una venta ya `CANCELADO` responde `200` sin reintegrar stock ni publicar un nuevo evento. Publica evento `"CANCELADA"` solo después del commit.
- **Request:** ruta `{id}`. Sin body.
- **Response `200 OK`:** sin cuerpo.
- **Errores:**
  - `404` si la venta no existe o pertenece a otro negocio: `"No existe la venta solicitada para cancelar."`
  - `404` si un producto del detalle ya no existe; la transacción revierte todo (stock y venta).
  - `409` si el estado no permite cancelar (p. ej. `PAGADO` o `ENVIADO`): `"La venta no se puede cancelar en su estado actual: X."`
  - `409` si falla la persistencia dentro de la transacción; no hay cambios parciales.

---

## 9. Módulo Carrito

El carrito se persiste en Redis por **negocio + cliente** con TTL de 24 horas. El `idNegocio` y el `idCliente` se obtienen del token autenticado; el body nunca define el dueño del carrito. El precio, el nombre y el stock se releen de MySQL al consultar o modificar el carrito.

**Endpoints (requieren `Authorization: Bearer <token>`):**

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/carrito` | Devuelve el carrito actual. |
| `POST` | `/api/carrito/items` | Agrega unidades de un producto y devuelve el carrito actualizado. |
| `DELETE` | `/api/carrito/items/{idProducto}` | Quita el producto y devuelve el carrito actualizado. |
| `DELETE` | `/api/carrito` | Vacía el carrito (`204 No Content`). |

**Request de `POST /api/carrito/items`:**

```json
{ "idProducto": 2, "cantidad": 1 }
```

**Response de `GET`, `POST` y `DELETE /items/{idProducto}`:**

```json
{
  "idCliente": 1,
  "items": [
    {
      "idProducto": 2,
      "nombre": "Yerba mate",
      "precio": 1500.00,
      "cantidad": 2,
      "subtotal": 3000.00
    }
  ],
  "total": 3000.00
}
```

**Errores:**
- `400` cantidad o producto inválido, o método no admitido en la ruta.
- `401`/`403` token ausente, inválido, negocio no autorizado o negocio inactivo/dado de baja.
- `404` producto inexistente o inactivo en el negocio.
- `409` stock insuficiente o conflicto de concurrencia en Redis.
- `429` rate limit: `carrito-consulta` 240/IP, 240/cuenta, 600/negocio; `carrito-modificacion` 120/IP, 120/cuenta, 300/negocio (ventana de 60 s).

**Checkout:** el frontend envía el contenido actual del carrito en `POST /api/ventas/crear` (mismo contrato de §7.1); al confirmarse el pago, el frontend llama `DELETE /api/carrito`. El endpoint de checkout no consume el carrito de Redis para no alterar su contrato.

Reglas de dominio relevantes:
- Cantidad por ítem debe ser `> 0`.
- Al agregar un producto ya existente, las cantidades se **suman**.
- La cantidad total por producto no puede superar el `stockDisponible` actual.

---

## 10. Módulo Auditoría

**Ruta base:** `/api/auditoria` · **Controller:** `AuditoriaController` · **Service:** `AuditoriaService`

> Todas las consultas y registros se filtran por el negocio del token (`id_negocio`); un usuario de un negocio no puede leer ni escribir auditoría de otro. Las filas históricas sin `id_negocio` resoluble no se devuelven en consultas por negocio.

### 9.1 `GET /api/auditoria`

- **Caso de uso:** Trazabilidad completa (historial de operaciones).
- **Response `200 OK`** — array de entidad `Auditoria`:

```json
[
  {
    "id": 1,
    "idNegocio": 1,
    "accion": "CREAR",
    "entidad": "Producto",
    "idEntidad": 3,
    "usuario": "admin",
    "ipOrigen": "203.0.113.10",
    "resultado": "OK",
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

## 11. Módulo Stock

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

## 12. Módulo Recomendaciones

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

## 13. Guía de Implementación Frontend — `js/api.js`

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

## 14. Guía de Implementación Frontend — `js/app.js`

### 13.1 Estado global sugerido

```js
const state = {
  productos: [],      // ProductoResponseDTO[]
  clientes: [],       // ClienteResponseDTO[]
  cuentas: [],        // CuentaResponseDTO[]
  ventaActual: null,  // Venta | null
  carrito: [],        // items devueltos por GET /api/carrito
  filtros: { categoria: "", estadoStock: "", busqueda: "" },
};
```

### 13.2 Máquina de estados de Venta (mapeo de `EstadoVenta`)

El backend controla las transiciones con la siguiente matriz (cualquier otra combinación → `409` en `/cancelar`):

| `estadoCompra` | Acciones UI habilitadas |
|---|---|
| `PENDIENTE` | `Pagar` (`POST /ventas/{id}/pago`) y `Cancelar` (`POST /ventas/{id}/cancelar`) |
| `PAGADO` | ninguna en la UI actual (el backend admite `PAGADO → ENVIADO`, sin botón expuesto) |
| `ENVIADO` | ninguna (terminal) |
| `CANCELADO` | ninguna (terminal) |
| `EXPIRADO` | ninguna (terminal; lo asigna el job de expiración) |

Recomendación de UI por estado:

```js
const VENTA_UI = {
  PENDIENTE: { label: "Pendiente", color: "warning", acciones: ["pagar", "cancelar"] },
  PAGADO:    { label: "Pagado",    color: "success", acciones: [] },
  ENVIADO:   { label: "Enviado",   color: "info",    acciones: [] },
  CANCELADO: { label: "Cancelado", color: "danger",  acciones: [] },
  EXPIRADO:  { label: "Expirado",  color: "danger",  acciones: [] },
};
```

Regla de negocio visible para el usuario: **una venta `PENDIENTE` puede pagarse o cancelarse (reintegra stock); una venta `PAGADO` no puede cancelarse.** `ENVIADO`, `CANCELADO` y `EXPIRADO` son terminales. Cancelar una venta ya `CANCELADO` responde `200` sin efectos (idempotente). Una venta `EXPIRADO` no se reactiva si llega un pago tardío.

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
- **Tipo de pago:** para checkout online `["MERCADO_PAGO","STRIPE"]`; para cobro manual `["EFECTIVO","TRANSFERENCIA"]`. `TARJETA_CREDITO` existe en el enum pero no está soportado y se rechaza.
- **Acciones de auditoría:** `["CREAR","ACTUALIZAR","ELIMINAR","REGISTRAR_VENTA","CANCELAR_VENTA"]` (para `POST /api/auditoria` y filtros por acción).
- **Descuento:** tipo `["PORCENTAJE","MONTO","ESPECIAL"]`. Para `MONTO`, `valor` es importe en moneda; para `PORCENTAJE`, fracción (ej. `0.10` = 10%). Enviar `descuento: null` si no aplica.

### 13.5 Flujos críticos de UI

1. **Agregar al carrito → Crear venta:** persistir con `POST /api/carrito/items`; para el checkout construir el payload en el formato exacto del contrato 7.1 (`idCliente`, `tipoPago`, `descuento|null`, `items`). Validar antes `cantidad <= stockDisponible`.
2. **Pago:** llamar `pagarVenta(id)` y luego re-consultar `obtenerVenta(id)` (o re-listar) para refrescar el estado; mostrar `"PAGADO"`.
3. **Cancelación:** confirmación obligatoria en UI; tras éxito, refrescar producto(s) del detalle porque el stock se reintegra.
4. **Errores de negocio:** capturar `error.message` (extraído de `{"error": ...}`) y mostrarlo al usuario (ej. saldo insuficiente al pagar).

---

## 15. Anexo: Mapa de Respuestas / Códigos HTTP

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
| PUT `/api/clientes/{id}` | 200 | vacío | validación | — | no existe (404) |
| DELETE `/api/clientes/{id}` | 200 | vacío | id inválido | — | no existe (404) |
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
| POST `/api/ventas/{id}/cancelar` | 200 | vacío | – | venta/producto | 409 estado inválido |
| GET `/api/carrito` | 200 | CarritoResponse | – | – | – |
| POST `/api/carrito/items` | 200 | CarritoResponse | cantidad/producto | producto | – |
| DELETE `/api/carrito/items/{idProducto}` | 200 | CarritoResponse | id inválido | – | – |
| DELETE `/api/carrito` | 204 | vacío | – | – | – |
| GET `/api/auditoria` | 200 | array | – | – | – |
| POST `/api/auditoria` | 201 | vacío | acción/descripción | – | – |
| GET `/api/auditoria/producto/{id}` | 200 | array | id inválido | – | – |
| GET `/api/auditoria/accion/{accion}` | 200 | array | acción inválida | – | – |
| POST `/api/stock/evaluar` | 200 | string enum | – | producto | – |
| GET `/api/stock/{id}` | 200 | string enum | id inválido | producto | – |
| POST `/api/recomendaciones` | 200 | array | payload | producto base | – |

**Convenciones generales:** todo error responde `{"error":"<mensaje>","codigo":<status>,"timestamp":"<ISO-8601>"}`. Los métodos no soportados en una ruta responden `405`.

---

## 16. Advertencias y Caveats (Importante)

1. **PUT/DELETE de recursos inexistentes → `500` (no `404`).** `ProductoService.actualizarAsync` y `CuentaService.actualizarAsync` lanzan `RuntimeException` que el `BaseController` mapea a `500`. **El frontend debe comprobar existencia (GET previo) o tratar `500` como recurso ausente.**
2. **DELETE silencioso:** eliminar un ID inexistente en productos/cuentas no produce error (no-op); en clientes responde `404`. La UI debe asumir éxito y refrescar.
3. **Errores de pago de venta → `500`** (RuntimeException): cliente sin cuentas o saldo insuficiente. La **cancelación** responde `404` (venta/producto inexistente) o `409` (estado inválido o fallo transaccional) y es idempotente (ver §7.4).
4. **`cuit` no se persiste** en clientes (mapper lo omite). Respuesta siempre `cuit: ""`.
5. **`CuentaResponseDTO` no incluye `idCliente`.** Para mostrar el vínculo cuenta-cliente hay que cruzar con el módulo de clientes.
6. **Dos umbrales de stock diferentes** (10 en DTO de producto vs 5 en `/api/stock`). Usar el del DTO para la vitrina.
7. **`TARJETA_CREDITO` no está disponible**: no hay pasarela implementada. El checkout acepta `MERCADO_PAGO`/`STRIPE` y la confirmación manual acepta `EFECTIVO`/`TRANSFERENCIA`.
8. **Cancelación de venta:** la máquina de estados solo permite `PENDIENTE → CANCELADO`; `PAGADO` y `ENVIADO` responden `409`. La operación es transaccional e idempotente: una venta ya `CANCELADO` responde `200` sin reintegrar stock.
9. **`detalles[].precioUnitario`** ya incluye descuento prorrateado (con redondeo a 2 decimales). El total es `Σ(precioUnitario * cantidad)`.
10. **Persistencia:** los datos principales y los pagos (`pagos`) están en MySQL; el carrito está en Redis con TTL de 24 h y controller REST propio en `/api/carrito`. El endpoint `/api/metodos-pago` y su almacenamiento en memoria fueron eliminados.
11. **Serialización por reflexión:** los enums se envían como su nombre; `fechaCompra`/`fecha` como ISO `LocalDateTime`; no hay envoltorio `data:` en las respuestas.
