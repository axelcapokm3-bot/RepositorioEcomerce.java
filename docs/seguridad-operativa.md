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
| `STRIPE_ALLOWED_RETURN_ORIGINS` | Orígenes HTTPS de retorno de Stripe separados por coma | Sí (Stripe; si falta se usa `APP_FRONTEND_URL`) |
| `STRIPE_CONNECT_TIMEOUT_MS` / `STRIPE_READ_TIMEOUT_MS` | Timeouts de conexión/lectura con Stripe | No (5000/5000 por defecto) |
| `AUTH_TOKEN_SECRET` | Firma de tokens de sesión (≥ 32 caracteres) | Sí, siempre |
| `AUTH_BOOTSTRAP_TOKEN` | Alta del primer ADMIN (un solo uso) | Solo durante bootstrap |
| `AUTH_BOOTSTRAP_TOKEN_EXPIRA_HORAS` | Vigencia del token de bootstrap (1–720 h) | No (24 h por defecto) |
| `APP_PERFIL` | `dev` o `prod` | `prod` en producción |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes frontend separados por coma | Sí (sin `*`) |
| `APP_TRUSTED_PROXIES` | IPs de proxies que pueden enviar `X-Forwarded-For`/`X-Real-IP` | Sí detrás de proxy |
| `WEBHOOK_FIRMA_TOLERANCIA_SEGUNDOS` | Ventana del `ts` firmado por Mercado Pago | No (300 s por defecto) |
| `RATE_LIMIT_AUTH_LOGIN_MAX_INTENTOS` | Intentos por IP antes de bloquear el ingreso | No (5 por defecto) |
| `RATE_LIMIT_AUTH_LOGIN_BLOQUEO_SEGUNDOS` | Duración del bloqueo por IP en ingreso | No (60 s por defecto) |
| `RATE_LIMIT_CHECKOUT_MAX_INTENTOS_IP` | Intentos de checkout por IP antes de bloquear | No (20 por defecto) |
| `RATE_LIMIT_CHECKOUT_BLOQUEO_SEGUNDOS` | Duración del bloqueo por IP en checkout | No (60 s por defecto) |

Precedencia de configuración: propiedad del sistema Java → `config.properties`
local (solo desarrollo, ignorado por git, nunca se empaqueta en el `.jar`) →
variable de entorno. Los secretos de pago (`STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET`) usan `ConfigLoader.getSecretProperty`: propiedad del
sistema → variable de entorno → archivo, para que el entorno prevalezca en
producción.

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

1. Aplicar `migracion_token_bootstrap.sql` y definir
   `AUTH_BOOTSTRAP_TOKEN` con un valor temporal y aleatorio. Solo el operador
   debe conocerlo. El token se guarda como huella SHA-256, se consume una sola
   vez dentro de la transacción de registro y vence según
   `AUTH_BOOTSTRAP_TOKEN_EXPIRA_HORAS` (24 h por defecto). Sin MySQL disponible
   solo existe un fallback en memoria, no apto para producción.
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
5. **Eliminar `AUTH_BOOTSTRAP_TOKEN` del entorno y reiniciar.** El token ya
   quedó marcado como usado; desde entonces solo un ADMIN autenticado puede
   crear más ADMINs. Para emitir otro, borrar la fila de `tokens_bootstrap` y
   reiniciar con el nuevo valor.
6. Intentos posteriores con `rol: ADMIN` sin token ADMIN responden `409`.

## 6. Notas del frontend

- El token vive en `sessionStorage` (alcance de pestaña, se borra al cerrar).
  Decisión documentada: no se usa `localStorage` persistente.
- Cookie `HttpOnly` evaluada y descartada por ahora: la API es Bearer
  stateless entre orígenes (`:5500` → `:8080`) y requeriría CORS con
  credenciales + protección CSRF. Queda como mejora futura.
- Ante `401` el frontend limpia la sesión y vuelve a `login.html`; ante `403`
  muestra falta de permisos sin reintentar.

## 7. Escaneo de secretos antes de commit

Ejecutar desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\scan-secrets.ps1
```

El script solo revisa archivos trackeados por Git e ignora `.git`, `target`,
`node_modules`, `bin`, `build`, `dist`, `out`, `coverage` y binarios/archivos
comprimidos. Devuelve `0` si no hay hallazgos, `1` si encuentra posibles
secretos y `2` si no puede resolver el repositorio. Nunca imprime el valor
detectado: solo ruta, línea y tipo. Ejecutarlo antes de cada commit y en CI.
Si hay un falso positivo, documentarlo en la revisión; no desactivar el
escaneo global.

## 8. Parámetros de hash de contraseñas (BA-03)

Decisión de hardening (2026-10-26): se mantiene **PBKDF2-HMAC-SHA256 con
210.000 iteraciones**, sal de 16 bytes y hash de 256 bits. No se trata de un
defecto corregible en este bloque: el costo es aceptable para el volumen actual
y los parámetros están fijados en `AdaptadorContrasena`, con regresión en
`AdaptadorContrasenaTest`.

Alternativas evaluadas y no adoptadas ahora:

- **Argon2id**: requiere una dependencia nueva y un cambio de formato de hash.
- **Subir a ≥ 600.000 iteraciones**: duplica el costo de cada login sin
  evidencia de necesidad operativa.
- **Tope de iteraciones leídas de la base**: un hash manipulado requeriría
  acceso de escritura a MySQL; el tope podría romper una migración futura. Se
  mantiene como riesgo residual aceptado y revisable.

Cualquier cambio de parámetros debe registrarse como decisión nueva y migrar el
hash de forma transparente al iniciar sesión.

## 9. Arranque fail-fast y diagnóstico (BA-02)

La inicialización de HikariCP sigue siendo fail-fast: si falta configuración o
la base no está disponible, la aplicación no arranca. El fallo se emite en una
sola línea saneada con el formato:

```text
event=startup.fail component=<componente> error=<tipo>: <mensaje>
```

`DiagnosticoArranque` centraliza el formato y `SanitizadorLog` elimina
credenciales y saltos de línea. `DatabaseConnection` conserva la causa original
dentro de `ExceptionInInitializerError` y `ServerMain` registra el evento antes
de terminar con código de salida `1`. No se agrega health check ni reintento en
este bloque.

## 10. Higiene local y artefactos (BA-06)

- `config.properties` en la raíz, `src/main/resources` y `bin/` está ignorado
  por Git y excluido del artefacto Maven; no se versiona ni se comparte.
- `classpath.txt`, los archivos vacíos trackeados y `app.jfr` ya no forman
  parte del repositorio. `*.jfr` y `classpath.txt` están en `.gitignore`.
- Escaneo de secretos del 2026-10-26: sin hallazgos en 537 archivos trackeados.
- Los archivos locales de configuración contienen valores no-placeholder. No se
  imprimieron ni se modificaron en este bloque. **Hay duda razonable sobre su
  origen**: si alguna vez fueron credenciales reales, rotarlas según las
  secciones 2 y 3 antes de reutilizarlas. La rotación externa queda a cargo del
  operador; no se realiza desde el repositorio.
