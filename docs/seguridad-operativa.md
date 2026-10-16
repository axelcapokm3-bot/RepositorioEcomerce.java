# Seguridad operativa — eCommerce Backend

Guía para el operador: rotación de secretos, variables de entorno, migración de
cuentas y creación del primer administrador. No contiene valores reales.

## 1. Variables de entorno

| Variable | Uso | Obligatoria en prod |
|---|---|---|
| `ECOMMERCE_DB_URL` | JDBC de MySQL | Sí |
| `ECOMMERCE_DB_USERNAME` | Usuario MySQL | Sí |
| `ECOMMERCE_DB_PASSWORD` | Contraseña MySQL | Sí |
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

## 4. Migración de cuentas

Archivo: `migracion_cuentas_auth.sql` (raíz del proyecto).

1. Respaldo previo: `mysqldump ecommerce_db > respaldo_YYYYMMDD.sql`.
2. Verificar columnas si la tabla `cuentas` ya existía:
   `SHOW COLUMNS FROM cuentas;`
   Requeridas: `id`, `nombre_cuenta`, `correo_electronico` (UNIQUE),
   `contrasenia`, `rol_usuario`, `id_cliente`.
3. Ejecutar: `mysql ecommerce_db < migracion_cuentas_auth.sql`.
   Es idempotente: si la tabla ya existe no modifica ni borra nada.
4. No ejecutar contra producción sin ventana de mantenimiento ni respaldo.

## 5. Primer administrador (bootstrap)

1. Definir `AUTH_BOOTSTRAP_TOKEN` con un valor temporal y aleatorio. Solo el
   operador debe conocerlo.
2. Crear el cliente dueño en `POST /api/clientes` (requiere ADMIN… si aún no
   hay ADMIN, crearlo directo en la tabla `clientes` con SQL).
3. Registrar el admin:
   `POST /api/auth/registro` con body
   `{"nombreCuenta":"...","correoElectronico":"...","contraseniaPlana":"...",
   "idCliente":<id>,"rol":"ADMIN"}` y header `X-Setup-Token: <valor>`.
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
