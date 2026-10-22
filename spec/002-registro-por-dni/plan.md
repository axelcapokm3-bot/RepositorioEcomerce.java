# PLAN — Registro por DNI

## 1. Alcance e Impacto

- **Especificación:** `spec/002-registro-por-dni/spec.md` y este plan.
- **Impacto:** `HIGH` — cambia el contrato de `POST /api/auth/registro` y toca
  capas de aplicación, infraestructura, presentación y frontend de autenticación.
- **Fuera de alcance:** `POST /api/auth/vincular` sigue con `idCliente`; no hay
  DDL ni migraciones.

## 2. Arquitectura y Componentes

- **CapaAplicacion:**
  - `IClienteRepositorio` agrega `Optional<Cliente> buscarPorDni(long idNegocio, String dni)`.
  - `CuentaRegistroDTO`: `idCliente` (int) → `dniCliente` (String) con
    validación espejo de `ClienteRequestDTO` (obligatorio, 7-8 dígitos).
  - `AutenticacionService.registrar`: resuelve el DNI antes de crear o
    reutilizar la cuenta; si no existe, lanza `IllegalArgumentException` con
    mensaje "No existe un cliente con el DNI indicado en este negocio." y no
    persiste cuenta. `validarPerfil` se conserva para `vincularNegocio`.
- **CapaInfraestructura:** `MySqlRepositorioCliente` y `RepositorioCliente`
  implementan `buscarPorDni` (sin filtrar por `activo`, misma semántica que
  `buscarPorId`).
- **CapaPresentacion:** `AuthController` no cambia (el DTO se deserializa solo).
- **Frontend:** `login.html` cambia label/input/validación/payload (`dniCliente`);
  `js/api.js` no cambia.

## 3. Persistencia y Verificación

- Sin cambios de esquema: `clientes.dni` ya existe (VARCHAR, 7-8 dígitos).
- `buscarPorDni` filtra por `dni = ? AND id_negocio = ?`.
- Tests: `AutenticacionServiceTest` (casos DNI válido, inexistente, de otro
  negocio; doble `ClienteRepositorioFalso` con `buscarPorDni`),
  `IdentidadGlobalMySqlTest` (obtiene el DNI del cliente libre).
- Verificación final: `mvn test` y prueba HTTP real del registro.

## 4. Estrategia y Orden de Implementación

1. Contrato: `IClienteRepositorio.buscarPorDni` e implementaciones.
2. DTO y servicio con resolución por DNI (antes del alta de cuenta).
3. Frontend del registro.
4. Tests y documentación (`API_DOCUMENTATION.md`, `MEMORY.md`).
5. **Verificación:** `mvn test`; prueba E2E con DNI inexistente (no crea
   cuenta) y DNI válido (vincula el ID correcto, con limpieza de datos).

## 5. Aprobación

- **Estado:** implementado y verificado el 2026-10-18: `mvn test` con 145
  pruebas (0 fallos; incluye `IdentidadGlobalMySqlTest` contra MySQL real),
  parseo HTTP de `dniCliente` verificado y validación del formulario en
  navegador (DNI inválido bloqueado sin request; payload con `dniCliente` y
  header `X-Negocio-Subdominio`). Decisión de diseño aprobada por el usuario el
  2026-10-18.
