# Seguridad operativa — eCommerce Backend

Guía para el operador: rotación de secretos, variables de entorno, migración de
cuentas y creación del primer administrador. No contiene valores reales.

## 1. Variables de entorno

| Variable | Uso | Obligatoria en prod |
|---|---|---|
| `ECOMMERCE_DB_URL` | JDBC de MySQL | Sí |
| `ECOMMERCE_DB_USERNAME` | Usuario MySQL | Sí |
| `ECOMMERCE_DB_PASSWORD` | Contraseña MySQL | Sí |
| `REDIS_HOST` / `REDIS_PORT` | Endpoint Redis del carrito | Sí en producción |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | Credenciales Redis (ACL) | Si Redis requiere auth |
| `REDIS_SSL` | TLS hacia Redis (`true`/`false`) | Según el proveedor |
| `MERCADOPAGO_ACCESS_TOKEN` | Pagos Mercado Pago | Sí (si se usa MP) |
| `MERCADOPAGO_WEBHOOK_SECRET` | Firma webhook MP | Sí (si se usa MP) |
| `MERCADOPAGO_PUBLIC_BASE_URL` | Retorno checkout MP | Sí (si se usa MP) |
| `STRIPE_SECRET_KEY` | Pagos Stripe | Sí (si se usa Stripe) |
| `STRIPE_WEBHOOK_SECRET` | Firma webhook Stripe | Sí (si se usa Stripe) |
| `AUTH_TOKEN_SECRET` | Firma de tokens de sesión (≥ 32 caracteres) | Sí, siempre |
| `AUTH_BOOTSTRAP_TOKEN` | Alta del primer ADMIN (temporal) | Solo durante bootstrap |
| `APP_PERFIL` | `dev` o `prod` | `prod` en producción |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes frontend separados por coma | Sí (sin `*`) |

Precedencia de configuración: propiedad del sistema Java → `config.properties`
local (solo desarrollo, ignorado por git, nunca se empaqueta en el `.jar`) →
variable de entorno.

## 2. Rotación de secretos de pago

Las credenciales reales anteriores deben considerarse expuestas si alguna vez
estuvieron en un archivo versionado o en un artefacto compartido.

1. Mercado Pago: Dashboard → Credenciales → revocar el access token anterior y
   generar uno nuevo. Webhooks → regenerar el secreto HMAC.
2. Stripe: Dashboard → Developers → API keys → rotar `sk_*`. Webhooks →
   seleccionar el endpoint → revelar/rotar el signing secret (`whsec_*`).
3. Configurar los valores nuevos **solo** como variables de entorno del proceso
   Java (sección 1). No guardarlos en archivos del repositorio.
4. Reiniciar el backend y verificar:
   - `POST /api/ventas/crear` genera checkout con la pasarela.
   - Los webhooks responden `200` con firmas válidas y `401/400` con firmas
     inválidas.
5. Verificar que ningún secreto real quede en `git log`, `config.properties.example`,
   scripts `.sql` ni documentación.

## 3. Secreto de tokens (`AUTH_TOKEN_SECRET`)

- Generar uno aleatorio de al menos 32 caracteres, por ejemplo con:
  `openssl rand -base64 48`.
- Con `APP_PERFIL=prod`, el servidor **no inicia** sin este secreto.
- En desarrollo (sin `APP_PERFIL=prod`) se usa un secreto efímero solo local;
  los tokens se invalidan al reiniciar. No apto para producción.
- Rotarlo invalida todas las sesiones activas (los usuarios deben ingresar de
  nuevo). No hay lista de revocación: la expiración máxima es 72 h.

## 4. Identidad global y `cuentas_negocio` (esquema real)

Estructura **ya aplicada** en `ecommerce_db` (verificada 2026-10-16 con
`information_schema` y `SHOW CREATE TABLE`). No requiere DDL, migración ni
respaldo: el código se adaptó a esta estructura.

- `cuentas` — identidad global: `id` BIGINT autoincremental, `nombre_cuenta`,
  `correo_electronico` (VARCHAR 254, UNIQUE `uq_cuentas_correo`), `contrasenia`
  (hash PBKDF2), `activo` TINYINT(1), marcas de tiempo. Una cuenta no pertenece
  a ningún negocio por sí sola.
- `cuentas_negocio` — vínculo cuenta↔negocio sin ID propio (PK compuesta
  `(id_cuenta, id_negocio)`): `id_cliente` NOT NULL con FK compuesta
  `(id_cliente, id_negocio)` → `clientes(id, id_negocio)` (el perfil de cliente
  se comparte con las tablas comerciales), `rol_usuario` (`ADMIN`/`CLIENTE`),
  `activo` TINYINT(1). UNIQUE `(id_negocio, id_cliente)`: un perfil de cliente
  pertenece a una sola cuenta por negocio.
- Mapeo de estados: el esquema solo distingue `activo` 0/1. El dominio conserva
  `EstadoCuenta`; `ACTIVA` ⇄ `1` y `BLOQUEADA`/`BAJA` ⇄ `0` (al leer, un `0` se
  reporta como `BAJA`; la distinción `BLOQUEADA`/`BAJA` no se persiste).
- Negocio activo de la sesión: el token firmado (5 campos:
  `idCuenta:idCliente:idNegocio:rol:expiracion`) fija el negocio; se cambia con
  `POST /api/auth/negocio-activo`, que reemite el token solo si existe vínculo
  activo y el negocio está activo. En requests autenticados el tenant sale del
  token y un header de negocio distinto responde 403, salvo en
  `POST /api/auth/negocio-activo` y `POST /api/auth/vincular`: allí el negocio
  objetivo se resuelve del subdominio (header/Host) porque la operación es
  explícitamente cross-tenant; el JWT identifica la cuenta, el body no acepta
  `idNegocio` y el servicio valida vínculo/identidad antes de emitir el token.
- Verificación de compatibilidad (solo lectura, sin efectos):
  `SHOW CREATE TABLE cuentas;` y `SHOW CREATE TABLE cuentas_negocio;` deben
  mostrar lo descrito arriba, y
  `SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='ecommerce_db' AND TABLE_NAME='cuentas_negocio' AND REFERENCED_TABLE_NAME IS NOT NULL;`
  debe devolver 4 filas de FK (cliente compuesta, cuenta, negocio).
- El archivo `migracion_identidad_global.sql` quedó **obsoleto** (nunca se
  ejecutó; contenía DDL de una versión previa del diseño). No usarlo.

### Estados no representables (limitación conocida)

Si en el futuro se necesita distinguir cuenta bloqueada de cuenta dada de baja,
requiere una columna nueva en `cuentas`/`cuentas_negocio` con aprobación
explícita; hoy ambas se persisten como `activo = 0`.

## 5. Primer administrador (bootstrap)

1. Definir `AUTH_BOOTSTRAP_TOKEN` con un valor temporal y aleatorio. Solo el
   operador debe conocerlo.
2. Crear el cliente dueño en `POST /api/clientes` (requiere ADMIN… si aún no
   hay ADMIN, crearlo directo en la tabla `clientes` con SQL).
3. Registrar el admin:
   `POST /api/auth/registro` con body
   `{"nombreCuenta":"...","correoElectronico":"...","contraseniaPlana":"...",
   "dniCliente":"<dni>","rol":"ADMIN"}` y header `X-Setup-Token: <valor>`. Si el
   DNI no existe en el negocio, agregar también `nombreCliente`,
   `apellidoCliente`, `fechaNacimientoCliente`, `direccionCliente` y
   `localidadCliente` para crear el perfil de cliente.
4. Verificar ingreso en `POST /api/auth/ingreso` y acceso a una ruta ADMIN
   (p. ej. `GET /api/auditoria` con `Authorization: Bearer <token>`).
5. **Eliminar `AUTH_BOOTSTRAP_TOKEN` del entorno y reiniciar.** Desde entonces,
   solo un ADMIN autenticado puede crear más ADMINs.
6. Intentos posteriores con `rol: ADMIN` sin token ADMIN responden `409`.

## 6. Notas del frontend

- El token vive en `sessionStorage` (alcance de pestaña, se borra al cerrar).
  Decisión documentada: no se usa `localStorage` persistente.
- Cookie `HttpOnly` evaluada y descartada por ahora: la API es Bearer
  stateless entre orígenes (`:5500` → `:8080`) y requeriría CORS con
  credenciales + protección CSRF. Queda como mejora futura.
- Ante `401` el frontend limpia la sesión y vuelve a `login.html`; ante `403`
  muestra falta de permisos sin reintentar.
