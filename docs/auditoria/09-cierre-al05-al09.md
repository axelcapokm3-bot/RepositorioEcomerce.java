# Cierre AL-05, AL-06, AL-07 y AL-09

## Alcance

Se cerraron los hallazgos P1 de compra directa, estado de sesión, caché de tenant e
idempotencia de ventas. No se modificó la lógica de identidad CR-01/CR-02, el
Bloque 2 de pagos ni `ME-05`.

## Cambios

### AL-05 - Productos inactivos y negocio inactivo

- `Negocio.estaOperativo()` exige `activo` y `fechaBaja == null`.
- `Producto.puedeVenderse()` centraliza la regla de venta.
- `CarritoService`, `VentaController` y `VentaService.validarYActualizarStock`
  rechazan productos no vendibles. El carrito y la compra directa también
  rechazan un negocio inactivo.
- El catálogo público lista y resuelve solo productos activos. Un administrador
  autenticado conserva el listado completo y la consulta por ID.

### AL-06 - Revocación efectiva de tokens

- Se agregó `IValidadorSesion` y la implementación `MySqlValidadorSesion`.
- `ProveedorTokenAcceso.validar` verifica en cada solicitud que la cuenta, el
  vínculo, el rol, el negocio y el cliente sigan activos.
- Un fallo de MySQL durante la verificación falla cerrado con error 500; no se
  acepta el token.
- Los tests unitarios usan un validador falso; la integración MySQL cubre cambio
  de rol, baja de vínculo y baja de cuenta.

### AL-07 - Caché de tenant con vencimiento e invalidación

- `ResolutorNegocio` guarda `idNegocio` con vencimiento.
- TTL configurable con `negocio.cache.ttl-segundos` (60 segundos por defecto),
  también disponible como `NEGOCIO_CACHE_TTL_SEGUNDOS`.
- `NegocioConfigController` invalida el subdominio después de actualizar su
  configuración. `invalidarTodo()` queda disponible para operaciones masivas.

### AL-09 - Idempotencia por negocio

- `MySqlRepositorioVenta` detecta el duplicado del índice de idempotencia y lo
  convierte en `ReglaNegocioException`.
- `BaseController` responde 409 ante esa regla de negocio.
- `VentaService` conserva el reintento de lectura por `(idNegocio, clave)`, por
  lo que una carrera devuelve la venta existente y una colisión cross-tenant
  termina en 409.
- `migracion_idempotencia_ventas_pagos.sql` ahora declara
  `uq_ventas_negocio_idempotencia (id_negocio, clave_idempotencia)` y documenta
  la verificación previa y el retiro del índice global anterior.

## Verificación

- `mvn test`: 278 tests, 0 fallos, 0 errores, 1 omitido (prueba de credenciales de
  Mercado Pago que requiere variables de entorno).
- Nuevas pruebas: `ProveedorTokenAccesoTest`, `CarritoServiceTest`,
  `ProductoServiceTest`, `VentaServiceTest`, `MySqlRepositorioVentaTest`,
  `ResolutorNegocioTest`, `VentaControllerTenantTest` e
  `IdentidadGlobalMySqlTest`.

## Riesgos residuales

- La validación de sesión consulta MySQL en cada solicitud autenticada. Es el
  precio de la revocación inmediata; si se necesita reducir latencia, el próximo
  paso es un caché corto con invalidación explícita.
- El TTL de tenant acota cambios externos, pero una desactivación hecha por SQL
  puede tardar hasta el TTL configurado si no se invalida explícitamente.
- Los índices globales de `pagos` siguen fuera de este cierre y corresponden al
  Bloque 2.
