# PLAN — Registro con creación automática del cliente

## 1. Alcance e Impacto

- **Especificación:** `spec/003-registro-cliente-automatico/spec.md` y este plan.
- **Impacto:** `HIGH` — cambia el contrato de `POST /api/auth/registro` (campos
  nuevos), escribe en `clientes` desde el alta pública y toca frontend de
  autenticación. Área crítica (autenticación/persistencia de identidad).
- **Fuera de alcance:** `POST /api/auth/vincular` (sigue con `idCliente`);
  actualización de datos de clientes existentes desde el registro; DDL.

## 2. Arquitectura y Componentes

- **CapaAplicacion:**
  - `CuentaRegistroDTO`: agrega `nombreCliente`, `apellidoCliente`,
    `fechaNacimientoCliente` (`LocalDate`), `direccionCliente`,
    `localidadCliente` (opcionales; validación plena al usarlos).
  - `AutenticacionService.registrar`: reordena para validar antes de persistir
    y resuelve DNI existente (vincular) o nuevo (crear con
    `ClienteRequestDTO` + `ClienteMapper.toEntidad` + `guardar`, con el mismo
    fallback de ID que usa la cuenta).
- **CapaInfraestructura:** sin cambios (`MySqlRepositorioCliente.guardar` ya
  soporta alta con ID generado y `buscarPorDni` ya existe).
- **CapaPresentacion:** `AuthController`/`JsonUtils` sin cambios (`JsonUtils`
  ya coerciona `String` → `LocalDate` y `Map` → record).
- **Frontend (`login.html`):** campo `regNegocioInput` visible, precarga y
  sincronización con `negocioInput`, cinco campos de cliente con validación
  local (presencia, DNI `^\d{7,8}$`, fecha con edad ≥ 18), payload extendido.

## 3. Persistencia y Verificación

- Sin cambios de esquema. Restricciones existentes:
  `uq_clientes_negocio_dni (id_negocio, dni)`.
- Carreras de registros simultáneos quedan fuera de alcance (sin transacción
  distribuida): se documentan como limitación conocida.
- Tests: `AutenticacionServiceTest` (doble con asignación de ID en
  `guardar`; casos: crea cliente; no sobrescribe existente; datos inválidos no
  persisten; correo duplicado no crea cliente; vínculo existente no crea
  cliente) y `IdentidadGlobalMySqlTest` (alta real de cliente nuevo con
  limpieza de datos).
- Verificación final: `mvn test` y prueba HTTP real del registro con DNI
  nuevo (crea cliente + sesión) y con DNI existente (vincula).

## 4. Estrategia y Orden de Implementación

1. `CuentaRegistroDTO` con campos nuevos.
2. `AutenticacionService.registrar` reordenado + creación del cliente.
3. Frontend del registro (subdominio visible + datos del cliente + payload).
4. Tests de aplicación e integración.
5. Documentación (`API_DOCUMENTATION.md`, `MEMORY.md`) y este spec (criterios
   marcados).
6. **Verificación:** `mvn test` completo; luego, con el backend reiniciado,
   alta E2E real de un cliente nuevo y verificación en MySQL.

## 5. Aprobación

- **Estado:** implementado y verificado el 2026-10-18. Decisión de diseño
  aprobada por el usuario el 2026-10-18 (auto-creación de cliente y subdominio
  visible en Registrarse). `mvn test`: 151 pruebas, 0 fallos (incluye
  `IdentidadGlobalMySqlTest` con alta real de cliente nuevo y limpieza). E2E
  HTTP contra el servidor pendiente de recompilar/levantar por el usuario.
