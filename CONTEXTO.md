# CONTEXTO DEL PROYECTO: eCommerce-Java

Este documento proporciona una visión general y exhaustiva de la arquitectura, catálogo de endpoints, DTOs, modelos y reglas de negocio del sistema eCommerce-Java. Sirve como referencia técnica oficial para desarrolladores e integradores del sistema.

---

## 1. Visión General de la Arquitectura

El sistema está diseñado bajo un enfoque de **Arquitectura en Capas** que promueve el desacoplamiento, la mantenibilidad y la separación de responsabilidades:

```
[ CapaPresentacion (HTTP Controllers) ]
                 │
                 ▼
[ CapaAplicacion (Services, DTOs, Mappers) ] <───► [ CapaDominio (Entidades, Enums) ]
                 │
                 ▼
[ CapaInfraestructura (Repositories, EventBus) ]
```

### Detalle de las Capas:
1. **Capa de Presentación (`CapaPresentacion`):** 
   - Aloja a los controladores HTTP (`ProductoController`, `ClienteController`, `CuentaController`, `VentaController`) que extienden de [BaseController](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaPresentacion/Controllers/BaseController.java).
   - Implementa `HttpHandler` de Java SE para atender peticiones en puertos específicos (puerto por defecto `8080`).
2. **Capa de Aplicación (`CapaAplicacion`):**
   - Contiene los servicios de aplicación (`ProductoService`, `ClienteService`, `CuentaService`, `VentaService`, `AuditoriaService`, `RecomendacionService`, `StockService`).
   - Define los contratos de servicios y repositorios (`Interfaces`), los DTOs de entrada y salida, y los mappers para la conversión DTO-Entidad.
   - Cuenta con una carpeta alternativa de controladores desacoplada de HTTP (`CapaAplicacion.Controller`).
3. **Capa de Dominio (`CapaDominio`):**
   - Contiene las entidades esenciales de negocio (`Producto`, `Cliente`, `Cuenta`, `Carrito`, `ItemCarrito`, `Venta`, `DetalleVenta`, `Auditoria`).
   - Define los enums de estado y categoría, las estrategias de descuento (`IDescuentos` y sus implementaciones concretas) y los eventos del dominio.
4. **Capa de Infraestructura (`CapaInfraestructura`):**
   - Implementaciones concretas de acceso a datos en memoria utilizando colecciones thread-safe como `CopyOnWriteArrayList` y variables atómicas.
   - Contiene el bus de eventos en memoria (`InMemoryEventBus`).
5. **Capa Externa (`CapaExterna`):**
   - Punto de entrada principal para levantar el servidor HTTP ([ServerMain](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaExterna/ServerMain.java)) o correr pruebas de consola ([Main](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaExterna/Main.java)).

### Manejo Asincrónico con `CompletableFuture`
La comunicación entre la capa de presentación y la capa de aplicación es completamente no bloqueante:
- Los métodos de los servicios de aplicación retornan instancias de `CompletableFuture<T>`.
- En la clase base [BaseController](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaPresentacion/Controllers/BaseController.java), el método utilitario `handleAsync` procesa los resultados de manera asíncrona mediante `.thenAccept(...)` y captura errores con `.exceptionally(...)`.
- En [ServerMain](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaExterna/ServerMain.java), se configura un pool de hilos dinámico (`Executors.newCachedThreadPool()`) como `Executor` del servidor HTTP para garantizar el procesamiento concurrente eficiente de las peticiones HTTP concurrentes.

---

## 2. Catálogo de Endpoints HTTP

Todos los endpoints se exponen bajo el prefijo `/api` en el puerto `8080`.

### 📦 Productos (`ProductoController`)

#### `GET /api/productos`
- **Descripción:** Obtiene la lista completa de productos.
- **Request:** Sin cuerpo.
- **Response:** `200 OK`
  - **Body (JSON):** Array de [ProductoResponseDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOsalida/ProductoResponseDTO.java)
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

#### `POST /api/productos`
- **Descripción:** Crea un nuevo producto y publica un evento en el bus de eventos.
- **Request:**
  - **Body (JSON):** [ProductoRequestDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOentrada/ProductoRequestDTO.java)
  ```json
  {
    "nombre": "Mouse Vertical",
    "precio": 50.0,
    "stock": 15,
    "categoria": "ELECTRONICA"
  }
  ```
- **Response:** `201 Created` (Sin cuerpo) o `400 Bad Request` si la validación falla (ej. precio negativo).

#### `GET /api/productos/{id}`
- **Descripción:** Busca un producto específico por ID.
- **Request:** Parámetro de ruta `{id}` (int).
- **Response:**
  - `200 OK` con el JSON de `ProductoResponseDTO`.
  - `404 Not Found` si el producto no existe.

#### `PUT /api/productos/{id}`
- **Descripción:** Actualiza los datos de un producto.
- **Request:** Parámetro de ruta `{id}`. JSON de `ProductoRequestDTO` en el Body.
- **Response:** `200 OK` o `400 Bad Request` si hay errores de validación.

#### `DELETE /api/productos/{id}`
- **Descripción:** Elimina un producto del sistema.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK` (Sin cuerpo).

---

### 👥 Clientes (`ClienteController`)

#### `GET /api/clientes`
- **Descripción:** Retorna todos los clientes registrados.
- **Request:** Sin cuerpo.
- **Response:** `200 OK` con un array de [ClienteResponseDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOsalida/ClienteResponseDTO.java).

#### `POST /api/clientes`
- **Descripción:** Registra un nuevo cliente y valida reglas clave de negocio (mayoría de edad, formato de DNI y CUIT).
- **Request:**
  - **Body (JSON):** [ClienteRequestDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOentrada/ClienteRequestDTO.java)
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
- **Response:** `201 Created` o `400 Bad Request` con mensaje de la validación infringida (ej: "El cliente debe ser mayor de 18 años para registrarse.").

#### `GET /api/clientes/{id}`
- **Descripción:** Obtiene los detalles de un cliente.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK` con el JSON de `ClienteResponseDTO` o `404 Not Found`.

#### `PUT /api/clientes/{id}`
- **Descripción:** Modifica los campos de un cliente existente.
- **Request:** Parámetro de ruta `{id}`. JSON de `ClienteRequestDTO` en el Body.
- **Response:** `200 OK`.

#### `DELETE /api/clientes/{id}`
- **Descripción:** Da de baja lógica/remueve un cliente por ID.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK`.

---

### 💳 Cuentas (`CuentaController`)

#### `GET /api/cuentas`
- **Descripción:** Lista todas las cuentas del sistema.
- **Request:** Sin cuerpo.
- **Response:** `200 OK` con array de [CuentaResponseDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOsalida/CuentaResponseDTO.java).

#### `POST /api/cuentas`
- **Descripción:** Registra una nueva cuenta vinculada a un cliente. Realiza la ofuscación y hashing de contraseña.
- **Request:**
  - **Body (JSON):** [CuentaRequestDTO](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/DTOentrada/CuentaRequestDTO.java)
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
- **Response:** `201 Created` o `400 Bad Request` si la contraseña no cumple la fuerza requerida o si el correo ya está en uso.

#### `GET /api/cuentas/{id}`
- **Descripción:** Obtiene los datos de una cuenta por su ID.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK` con el JSON de `CuentaResponseDTO` o `404 Not Found`.

#### `PUT /api/cuentas/{id}`
- **Descripción:** Actualiza los datos de la cuenta. Permite redefinir la contraseña (la cual se vuelve a encriptar).
- **Request:** Parámetro de ruta `{id}`. JSON de `CuentaRequestDTO` en el Body.
- **Response:** `200 OK`.

#### `DELETE /api/cuentas/{id}`
- **Descripción:** Elimina una cuenta.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK`.

#### `POST /api/cuentas/transferir`
- **Descripción:** Realiza una transferencia monetaria atómica entre dos cuentas.
- **Request:**
  - **Body (JSON):** `TransferRequest` (Record interno en `CuentaController`)
  ```json
  {
    "idCuentaOrigen": 1,
    "idCuentaDestino": 2,
    "monto": 1500.50
  }
  ```
- **Response:** `200 OK` (Sin cuerpo) o `400 Bad Request` si las cuentas no existen, el monto es negativo o hay saldo insuficiente.

#### `GET /api/cuentas/buscarPorCorreo`
- **Descripción:** Recupera una cuenta según su correo registrado.
- **Request:** Query Param `correo` (Ej: `/api/cuentas/buscarPorCorreo?correo=jperez@gmail.com`).
- **Response:** `200 OK` con el JSON de `CuentaResponseDTO` o `404 Not Found`.

---

### 🛒 Ventas (`VentaController`)

#### `POST /api/ventas/crear`
- **Descripción:** Inicia el procesamiento y persistencia de una orden de compra (venta) reduciendo stock físico.
- **Request:**
  - **Body (JSON):** `CreateVentaRequest` (Record interno de `VentaController`)
  ```json
  {
    "idCliente": 1,
    "tipoPago": "EFECTIVO",
    "descuento": {
      "tipo": "PORCENTAJE",
      "valor": 0.10
    },
    "items": [
      {
        "idProducto": 1,
        "cantidad": 2
      }
    ]
  }
  ```
- **Response:** `201 Created` o `400 Bad Request` si la venta no tiene ítems, un producto no existe o no tiene stock disponible.

#### `GET /api/ventas/{id}`
- **Descripción:** Retorna los detalles de la venta y sub-detalles de artículos asociados.
- **Request:** Parámetro de ruta `{id}`.
- **Response:** `200 OK` con la entidad [Venta](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaDominio/Entidades/Venta.java) en JSON o `404 Not Found`.

#### `POST /api/ventas/{id}/pago`
- **Descripción:** Registra de forma asíncrona la confirmación del pago de la venta.
- **Request:** Parámetro de ruta `{id}`. Sin cuerpo.
- **Response:** `200 OK` o `400 Bad Request` si la venta ya está pagada o cancelada.

#### `POST /api/ventas/{id}/cancelar`
- **Descripción:** Cancela una venta pendiente de pago y ejecuta la reposición de stock de forma atómica.
- **Request:** Parámetro de ruta `{id}`. Sin cuerpo.
- **Response:** `200 OK` o `400 Bad Request` si la venta ya fue pagada.

---

## 3. Modelos de Datos y DTOs

### Entidades y Modelos de Dominio

#### `Producto`
- `id` (int): Identificador único.
- `nombre` (String): Nombre descriptivo.
- `precio` (double): Precio unitario (mayor a 0).
- `stockFisico` (int): Cantidad física en bodega.
- `stockReservado` (int): Cantidad de artículos comprometidos.
- `categoria` (CategoriaProducto): Enum de categoría del producto.

#### `Cliente`
- `id` (int): Identificador único.
- `dni` (String): DNI (7 u 8 caracteres numéricos).
- `cuit` (String): CUIT (11 caracteres numéricos opcionales).
- `nombre` (String): Nombre del cliente.
- `apellido` (String): Apellido del cliente.
- `direccion` (String): Domicilio físico.
- `fechaNacimiento` (LocalDate): Fecha de nacimiento (valida >= 18 años).
- `localidad` (String): Localidad de residencia.
- `activo` (boolean): Flag de baja lógica.
- `motivoBaja` (String): Razón por la cual se inhabilitó.

#### `Cuenta`
- `id` (int): Identificador único de la cuenta.
- `nombreCuenta` (String): Nombre de usuario (sin espacios).
- `correoElectronico` (String): Email institucional/personal (debe contener `@` y terminar en `.com`).
- `contrasenia` (String): Password hasheado con BCrypt.
- `rolUsuario` (String): Rol de usuario asignado.
- `token` (String): Token temporal de sesión.
- `idCliente` (int): ID del Cliente asociado.
- `saldo` (BigDecimal): Saldo disponible en cuenta (no admite negativos).

#### `Venta`
- `id` (int): ID de transacción.
- `idCliente` (int): ID del cliente comprador.
- `fechaCompra` (LocalDateTime): Fecha y hora del registro.
- `estadoCompra` (EstadoVenta): Enum del estado actual.
- `tipoPago` (TipoDePago): Enum del medio de pago.
- `detalles` (List<DetalleVenta>): Colección de ítems facturados.

#### `DetalleVenta`
- `idProducto` (int): ID del producto.
- `precioUnitario` (double): Precio de venta final aplicado.
- `cantidad` (int): Cantidad de unidades compradas.

---

### Data Transfer Objects (DTOs)

#### Records de Entrada (`CapaAplicacion.DTOentrada`)
1. **`ProductoRequestDTO`:** `nombre` (String), `precio` (double), `stock` (int), `categoria` (CategoriaProducto).
2. **`ClienteRequestDTO`:** `dni` (String), `cuit` (String), `nombre` (String), `apellido` (String), `direccion` (String), `fechaNacimiento` (LocalDate), `localidad` (String).
3. **`CuentaRequestDTO`:** `nombreCuenta` (String), `correoElectronico` (String), `contraseniaPlana` (String), `idCliente` (int), `rolUsuario` (String), `saldo` (BigDecimal).

#### Records de Salida (`CapaAplicacion.DTOsalida`)
1. **`ProductoResponseDTO`:** `id` (int), `nombre` (String), `precio` (double), `stockFisico` (int), `stockReservado` (int), `stockDisponible` (int), `estadoStock` (EstadoStock), `categoria` (CategoriaProducto).
2. **`ClienteResponseDTO`:** `id` (int), `dni` (String), `cuit` (String), `nombreCompleto` (String), `direccion` (String), `fechaNacimiento` (LocalDate), `localidad` (String), `activo` (boolean).
3. **`CuentaResponseDTO`:** `id` (int), `nombreCuenta` (String), `correoElectronico` (String), `rolUsuario` (String), `token` (String), `saldo` (BigDecimal).

---

### Enums Globales del Dominio

* **`CategoriaProducto`:** `ELECTRONICA`, `ROPA`, `HOGAR`, `DEPORTES`, `LIBROS`.
* **`EstadoStock`:** `DISPONIBLE` (AReponer: 0), `CRITICO` (AReponer: 50), `AGOTADO` (AReponer: 100), `EN_REPOSICION` (AReponer: 0).
* **`EstadoVenta`:** `PENDIENTE`, `PAGADO`, `CANCELADO`.
* **`TipoDePago`:** `EFECTIVO`, `TARJETA`, `TRANSFERENCIA`.
* **`AccionAuditoria`:** `CREACION`, `MODIFICACION`, `ELIMINACION`, `INICIO_SESION`, `COMPRA`.

---

## 4. Reglas de Negocio Clave

### 🛡️ Validación de Contraseñas (BCrypt + Pepper)
El flujo de guardado de cuentas en [CuentaService](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/Service/CuentaService.java) protege las contraseñas mediante un proceso de dos factores criptográficos:

1. **Ofuscador Estricto (Pepper):** La contraseña provista se procesa en [OfuscadorDeContraseñas](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaAplicacion/Utilidades/OfuscadorDeContrase%C3%B1as.java). El algoritmo divide la contraseña a la mitad, inserta un Pepper estático (`"EcommerseStrictPepper2026"`) y luego invierte toda la cadena resultante.
2. **Hashing con Sal (BCrypt):** La cadena ofuscada resultante de mezclar la contraseña con el Pepper se pasa por `BCrypt.hashpw(passOfuscada, BCrypt.gensalt())` para generar el hash final que se almacena en la base de datos/repositorio.

### 📦 Gestión de Stock y Reposición Automática
El stock de los productos se actualiza dinámicamente según dos eventos:

* **Creación de Venta:** Cada ítem disminuye el stock físico mediante `reducirStock(cantidad)`. Si el stock llega a `0`, se invoca de manera automática la lógica interna `reponerStock()`.
* **Cancelación de Venta:** Al cancelar una venta en `VentaService.cancelarVentaAsync`, se itera sobre cada artículo del detalle y se reabastece el stock invocando a `producto.reponerStock()`.
* **Regla de Reposición (`EstadoStock`):** El comportamiento de reposición evalúa el stock físico del producto:
  - Si el stock actual es `0` (`AGOTADO`), se reponen automáticamente **100 unidades**.
  - Si el stock es crítico (entre `1` y `10` unidades, estado `CRITICO`), se reponen **50 unidades**.
  - Si el stock es saludable (`> 10` unidades, estado `DISPONIBLE`), no se realiza reposición alguna (**0 unidades**).

### 📢 Orquestación de Eventos (`EventBus`)
El bus de eventos en memoria [InMemoryEventBus](file:///c:/Users/Axel/Programacion%20Java/eCommerce/src/com/CapaInfraestructura/Implementacion/InMemoryEventBus.java) coordina las comunicaciones desacopladas entre diferentes servicios del sistema:

- **Publicación:** Los servicios llaman a `eventBus.publicar(evento)` para notificar cambios de estado.
  * `ProductoService` publica `ProductosEventos`.
  * `VentaService` publica `VentasEventos` (con acciones `"CREADA"`, `"PAGADA"` o `"CANCELADA"`).
- **Subscripción (Auditoría):** `AuditoriaService` se registra en el `EventBus` para consumir eventos de venta. Al detectar un evento de tipo `VentasEventos`, genera asíncronamente un registro de auditoría (`Auditoria`) persistido en su propio repositorio para registrar la trazabilidad transaccional.
