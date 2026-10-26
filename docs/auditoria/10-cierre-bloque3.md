# Cierre del Bloque 3 — AL-05, AL-06, AL-07 y AL-09

## Alcance

Se cerraron definitivamente los hallazgos del Bloque 3 con validación explícita,
lista negra de tokens, invalidación de caché por cambio de estado e identidad
multitenant 1:N. No se modificó la lógica de pagos del Bloque 2 ni la identidad
de perfiles libres del Bloque 1.

El esquema actual usa `cuentas.id` como identidad global y `cuentas_negocio`
como vínculo 1:N. Por eso el equivalente real de
`UNIQUE(id_negocio, id_usuario_global)` es `UNIQUE(id_negocio, id_cuenta)` y no
se agregó `clientes.id_usuario_global`. El script mantiene compatibilidad
condicional para esquemas externos que sí conserven esa columna.

## Cambios

### AL-05 — Validación explícita de negocio activo

- `ValidadorNegocioService` es el único punto que decide si un negocio está
  operativo (`exigirOperativo` / `estaOperativo`).
- `NegocioInactivoException` expresa la regla en dominio; `BaseController` la
  traduce a `403` y `VentaController` la maneja explícitamente.
- `CarritoService`, `VentaController` y `VentaService` dejaron de duplicar la
  consulta y ya no aceptan negocios inactivos o inexistentes. La compra directa
  responde `403` antes de persistir la venta.
- `AutenticacionService` usa el mismo validador antes de registrar, vincular,
  ingresar o cambiar de negocio.

### AL-06 — Lista negra explícita de tokens

- `IRevocacionToken` define el contrato; `RevocacionTokenMySql` persiste y
  `RevocacionTokenMemoria` cubre pruebas y entornos sin base.
- `HuellaToken` guarda solo SHA-256; nunca se persiste el JWT en claro.
- `TokenBlacklistService` recibe la expiración real del token y no registra
  tokens ya vencidos. El registro vive exactamente el tiempo restante del JWT.
- `ProveedorTokenAcceso.validar` consulta la lista negra en cada solicitud y
  falla cerrado si MySQL no responde. `revocar` valida firma y expiración antes
  de registrar la huella.
- `POST /api/auth/salida` revoca el token actual; el frontend lo invoca al
  cerrar sesión.
- `MySqlValidadorSesion` sigue cubriendo la revocación por cambios de rol,
  cuenta, vínculo o negocio. No existe un endpoint de bloqueo de usuario; si se
  agrega, debe revocar también sus tokens activos.

### AL-07 — Invalidación de caché por cambio de estado

- `IInvalidadorCacheNegocio` desacopla aplicación de presentación.
- `ResolutorNegocio.invalidarPorId` elimina todas las entradas del tenant sin
  depender de que el llamador conozca el subdominio.
- `NegocioService` invalida la caché después de actualizar configuración y en
  `darDeBaja`; `Negocio.darDeBaja` y `MySqlNegocioRepository.actualizarEstado`
  persisten el estado y la fecha de baja.
- `AutenticacionService` invalida la caché cuando detecta un negocio inactivo.

### AL-09 — Identidad multitenant 1:N

- `IdentidadDuplicadaException` extiende `ReglaNegocioException` y se mapea a
  `409` en `BaseController`.
- `MySqlRepositorioCliente` y `MySqlRepositorioCuentaNegocio` traducen el error
  MySQL `1062` (duplicado) a esa excepción de dominio. El `RuntimeException`
  resultante hace rollback en `JDBCTransaccion`; no queda estado parcial.
- `migracion_identidad_multitenant.sql` es idempotente y:
  - asegura `uq_clientes_negocio_dni (id_negocio, dni)`;
  - asegura `uq_cuentas_negocio_cliente (id_negocio, id_cliente)`;
  - asegura el equivalente `(id_negocio, id_cuenta)` si no lo cubre la PK;
  - elimina UNIQUE legados de una sola columna sobre `dni`, `id_cliente` o
    `id_cuenta`, creando antes el índice de soporte de las FK;
  - corrige `clientes.id_usuario_global` solo si esa columna existe.

## Verificación

Comandos ejecutados:

```powershell
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q "-Dtest=ValidadorNegocioServiceTest,TokenBlacklistServiceTest,NegocioServiceTest,CarritoServiceTest,VentaControllerTenantTest,ProveedorTokenAccesoTest" test
mvn test
```

Resultado de la suite completa: **295 tests, 0 fallos, 0 errores, 1 omitido**
(`PasarelaMercadoPagoTest` por credenciales de entorno). La integración MySQL se
ejecutó y quedó verde, incluidos `IdentidadGlobalMySqlTest` y
`ComprasMultiNegocioMySqlTest`.

Cobertura de los criterios de aceptación:

| Criterio | Prueba |
|---|---|
| Mismo usuario compra en dos negocios con perfiles separados | `ComprasMultiNegocioMySqlTest.compraEnDosNegociosSinMezclarDatos`; `IdentidadGlobalMySqlTest.permiteElMismoDniEnDosNegociosConLaMismaCuenta` |
| Token revocado se rechaza de inmediato | `ProveedorTokenAccesoTest.revocaTokenDeInmediato`; `revocaConElTiempoRestanteDeVidaDelToken`; `AuthControllerTenantTest.salidaRevocaToken` |
| Compra directa con negocio inactivo responde 403 | `VentaControllerTenantTest.rechazaNegocioInactivo` |
| Registro/ingreso rechaza negocio inactivo e invalida caché | `AutenticacionServiceTest.invalidaCacheAlRegistrarEnNegocioInactivo`; `invalidaCacheAlIngresarANegocioInactivo` |
| Cambio de estado/configuración invalida caché | `NegocioServiceTest`; `ResolutorNegocioTest.invalidaPorId` |

## Pruebas nuevas o actualizadas

- `ValidadorNegocioServiceTest` (3)
- `TokenBlacklistServiceTest` (4)
- `NegocioServiceTest` (2)
- `ComprasMultiNegocioMySqlTest` (1, integración MySQL)
- `IdentidadGlobalMySqlTest` (caso multitenant 1:N)
- `ProveedorTokenAccesoTest` (revocación inmediata y TTL)
- `AuthControllerTenantTest` (logout)
- `CarritoServiceTest` (excepción de dominio correcta)
- `VentaControllerTenantTest` (403)

## Riesgos residuales

- La lista negra agrega una consulta a MySQL por solicitud autenticada. Si la
  latencia es crítica, el siguiente paso es un caché corto con invalidación
  explícita, sin relajar el fail-closed.
- `tokens_revocados` debe purgarse periódicamente:
  `DELETE FROM tokens_revocados WHERE expira_en <= NOW();`.
- La migración no se ejecuta automáticamente en la suite; debe aplicarse una vez
  contra la base objetivo. Es idempotente.
- No hay endpoint de bloqueo de usuario. La sesión se invalida por el validador
  de estado; un bloqueo explícito futuro debe combinar ambas capas.
- Los índices globales de `pagos` siguen fuera de este cierre y corresponden al
  Bloque 2.
