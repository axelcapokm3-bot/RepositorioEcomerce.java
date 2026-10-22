# Cierre de remediación CR-01 y CR-02

## 1. Alcance

Este documento registra la implementación y las pruebas de la mitigación de:

- **CR-01**: reclamo de un perfil de cliente libre por parte de una cuenta sin
  vínculo previo.
- **CR-02**: uso de un correo existente por parte de un ADMIN de otro negocio
  para obtener una sesión sobre una cuenta ajena.

No cierra la auditoría completa ni los hallazgos AL/ME/BA. Tampoco modifica el
esquema de base de datos.

## 2. Decisiones implementadas

1. **Validación de identidad para perfiles libres.** Cuando el registro encuentra
   un cliente activo sin vínculo en el negocio, exige que
   `nombre`, `apellido` y `fechaNacimiento` coincidan con los datos persistidos.
   La comparación normaliza mayúsculas, tildes y espacios.
2. **Re-autenticación para correos existentes.** Un registro con un correo ya
   registrado solo continúa si la contraseña verifica. La cuenta no se duplica.
   Un ADMIN no puede operar una cuenta que no le pertenece.
3. **Una única transacción.** Lectura de cuenta, bloqueo del cliente, creación o
   reactivación del vínculo y creación del cliente ocurren en la misma conexión.
   El hash PBKDF2 se calcula fuera de la transacción.
4. **Vínculos activos.** `existeVinculo` y `listarNegocios` consideran solo
   vínculos activos; un vínculo inactivo se reactiva al volver a registrar.
5. **Contrato de vinculación.** `POST /api/auth/vincular` recibe DNI y datos de
   identidad del titular en lugar de `idCliente`. El servidor resuelve el
   cliente con `FOR UPDATE` dentro del negocio del token.

### Tensión con las recomendaciones de auditoría

La auditoría recomienda invitación por token (D-02) y prohibir el alta de
administradores con correo existente (D-03). El proyecto no dispone de
infraestructura real de envío de correo, por lo que se implementó
re-autenticación por contraseña e identidad. La validación de identidad es más
débil que una invitación: quien conozca nombre, apellido y fecha de nacimiento
de un titular libre aún podría reclamarlo con un correo nuevo. La invitación por
token sigue siendo la mitigación completa pendiente.

## 3. Cambios principales

- `AutenticacionService.registrar`: re-check de correo dentro de la transacción,
  verificación de contraseña para cuentas existentes, bloqueo del cliente,
  validación de identidad, creación de cliente y guardado/reactivación del
  vínculo. La sesión se emite después del commit.
- `AutenticacionService.vincularNegocio`: acepta DNI, nombre, apellido, fecha de
  nacimiento y contraseña; valida identidad y titularidad antes de crear el
  vínculo.
- `AutenticacionService.listarNegocios`: omite vínculos inactivos y negocios
  dados de baja.
- `MySqlRepositorioCuenta`: overloads con `Connection` para búsqueda por correo y
  guardado.
- `MySqlRepositorioCliente`: `buscarPorDniParaActualizar` con `FOR UPDATE` y
  guardado transaccional.
- `MySqlRepositorioCuentaNegocio`: adaptación a las columnas reales
  (`id_cuenta`, `id_negocio`, `id_cliente`, `rol_usuario`, `activo`),
  detección de duplicados, reactivación de vínculos inactivos y
  `existeVinculo` restringido a activos.
- `AuthController`: nuevo request de vinculación y validación de campos.
- `ServerMain`: inyección de `ITransaccion` en `AutenticacionService`.
- Frontend: `api.js` y el modal de vinculación envían DNI e identidad.
- `API_DOCUMENTATION.md`: contrato de registro, negocios y vinculación.

## 4. Verificación

Comandos ejecutados:

- `mvn -q -DskipTests compile`
- `mvn -q -DskipTests test-compile`
- `mvn -q -Dtest=AutenticacionServiceTest test`
- `mvn -q test`

Resultado del suite completo: **238 tests, 0 fallos, 0 errores, 4 omitidos**.

Los tests unitarios nuevos o ajustados cubren:

- identidad incorrecta sobre un perfil libre, sin persistencia;
- perfil ya vinculado a otra cuenta;
- reutilización de un vínculo inactivo;
- registro con correo existente y contraseña correcta;
- ADMIN que intenta vincular una cuenta de otro titular;
- negocio inactivo;
- listado que omite vínculos y negocios inactivos;
- vinculación con identidad de otro perfil;
- regresión del flujo exitoso de registro y vinculación;
- aislamiento de clientes: listar, buscar, actualizar y eliminar solo con el
  negocio del token; un ID de otro tenant responde `404` sin modificarlo;
- el header `X-Negocio-Subdominio` no puede reemplazar el negocio del token
  (`403`), y el alta de clientes asigna el negocio autenticado;
- `negocio-activo` y `vincular` ignoran `idNegocio` del body: el negocio
  objetivo sale del subdominio validado o, si no se indica, del JWT; un
  subdominio inexistente/inactivo responde `400` sin invocar al servicio.

`IdentidadGlobalMySqlTest` incluye la prueba de reclamo fallido y el flujo
legítimo, pero se omite en el entorno local porque el pool no puede autenticar
contra MySQL (`Public Key Retrieval is not allowed`). No es un fallo de código;
la configuración de integración debe habilitar el transporte requerido por el
servidor MySQL.

## 5. Riesgo residual

- La identidad declarada no prueba propiedad de la cuenta ni del correo. Queda
  pendiente la invitación por token o un canal de verificación equivalente.
- El orden de bloqueos usa cliente y luego vínculo; es consistente en los flujos
  revisados, pero no se ejecutó una prueba de concurrencia específica de esta
  remediación.
- La prueba de integración MySQL no se ejecutó en este entorno.
- Persisten los hallazgos AL/ME/BA de la auditoría.
