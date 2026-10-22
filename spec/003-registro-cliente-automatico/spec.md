# SPEC — Registro con creación automática del cliente

## 1. Problema

El registro público (`POST /api/auth/registro`) solo vincula la cuenta a un
cliente preexistente. Si el DNI no existe en el negocio, el alta falla y el
usuario no tiene forma de crear su perfil. Además, la pestaña "Registrarse" no
muestra el campo "Negocio (subdominio)": reutiliza en forma oculta el valor
guardado (posiblemente viejo o inválido), lo que produce
`400 "No se pudo identificar el negocio."` sin que el usuario pueda
detectarlo ni corregirlo.

## 2. Objetivo

Que el registro sea autosuficiente:

- DNI existente en el negocio → vincula la cuenta al cliente existente (sin
  modificar sus datos).
- DNI inexistente → crea el cliente en la tabla `clientes` con los datos
  aportados en el formulario, aplicando las mismas validaciones que el alta
  administrativa (`ClienteRequestDTO`), y luego crea la cuenta (hash PBKDF2) y
  el vínculo como `CLIENTE`.
- La pestaña "Registrarse" muestra y valida el subdominio del negocio.

## 3. Requisitos y Reglas de Negocio

- `CuentaRegistroDTO` agrega campos opcionales del cliente:
  `nombreCliente`, `apellidoCliente`, `fechaNacimientoCliente`,
  `direccionCliente`, `localidadCliente`. `dniCliente` conserva su validación.
- Si el DNI no existe, esos campos son obligatorios y se validan reutilizando
  `ClienteRequestDTO` (nombre/apellido solo letras y espacios, mayoría de 18
  años, fecha no futura, dirección y localidad obligatorias).
- Orden del servicio: validar TODO antes de persistir (correo duplicado,
  cuenta activa, vínculo existente, datos del cliente si el DNI es nuevo), y
  recién después persistir cuenta → cliente → vínculo. Así los errores
  comunes no dejan registros huérfanos.
- Si el DNI existe: el cliente no se modifica (los datos del formulario se
  ignoran en ese caso).
- Si la cuenta existe y el solicitante es ADMIN del negocio (alta de cuenta
  administradora): se reutiliza la cuenta sin pisar su contraseña.
- El `id_negocio` del cliente nuevo es el negocio resuelto del request.
- Sin cambios de esquema. `UNIQUE (id_negocio, dni)` en `clientes` y
  `UNIQUE (id_negocio, id_cliente)` en `cuentas_negocio` protegen carreras de
  registros simultáneos (el error se reporta como conflicto/error genérico).
- Frontend: la pestaña Registrarse incluye el campo "Negocio (subdominio)"
  visible y editable (precargado desde lo guardado), y campos de cliente con
  validación local de presencia, formato de DNI y edad (18+).

## 4. Casos de Uso

- Caso 1: DNI nuevo + datos completos → crea cliente, cuenta (hash) y vínculo;
  devuelve sesión con el `idCliente` nuevo.
- Caso 2: DNI existente → vincula al cliente existente; datos del formulario
  ignorados; el cliente no se modifica.
- Caso 3: DNI nuevo + datos incompletos o inválidos (menor de 18, sin
  dirección, etc.) → `400`; no se persiste cuenta ni cliente.
- Caso 4: correo ya registrado sin permiso de administrador → error; no se
  crea el cliente nuevo (validación previa a la persistencia).
- Caso 5: subdominio visible en Registrarse permite corregir el negocio y el
  error "No se pudo identificar el negocio" deja de ser inevitable.

## 5. Criterios de Aceptación

- [x] `CuentaRegistroDTO` acepta los cinco campos opcionales del cliente.
- [x] `AutenticacionService.registrar` crea el cliente cuando el DNI no
  existe, con `idNegocio` correcto y validaciones de `ClienteRequestDTO`.
- [x] Con datos de cliente inválidos o correo duplicado no se persiste nada
  nuevo.
- [x] El formulario de registro muestra subdominio + datos del cliente y arma
  el payload completo.
- [x] `mvn test` pasa completo, con tests nuevos para los casos 1–4.
- [x] Regresión E2E contra MySQL real (`IdentidadGlobalMySqlTest`).
- [x] `API_DOCUMENTATION.md` y `MEMORY.md` actualizados.

## 5.1 Corrección colateral necesaria

El alta del cliente fallaba en MySQL con CUIT vacío porque `Cliente` lo
inicializa como `""` y la constraint `chk_cuit_format` exige `NULL` o 11
dígitos. Se corrigió `MySqlRepositorioCliente.bind` para escribir `NULL`
cuando el CUIT está vacío (afectaba también al alta administrativa). Sin
cambios de esquema.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- Sin lambdas nuevas innecesarias; nombres en español en dominio/aplicación.
- No introducir tecnologías ni patrones nuevos; sin cambios de esquema.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión explícita del usuario | Estado/fecha |
|----------|-------------------------|--------------------------------|--------------|
| Auto-crear el cliente si el DNI no existe | Alternativas: mantener solo vinculación (cliente lo crea un ADMIN); dejar el alta pendiente de aprobación del ADMIN (requiere tabla/estado nuevo) | "Crear cliente automáticamente": el registro suma nombre, apellido, fecha de nacimiento, dirección y localidad | Aprobada por el usuario, 2026-10-18 |
| Subdominio visible en Registrarse | Alternativas: dejarlo solo en la pestaña Ingresar | "Mostrarlo en Registrarse": campo visible y editable en el formulario de registro | Aprobada por el usuario, 2026-10-18 |
| Campos del cliente en el request de registro | Alternativas: objeto anidado (`datosCliente`) vs. campos planos opcionales | Campos planos opcionales en `CuentaRegistroDTO` (solo obligatorios cuando el DNI no existe) | Decisión de diseño del agente, 2026-10-18 |
