# Cierre P3 — BA-01 a BA-06

> **Actualización 2026-10-26:** este documento registra el cierre inicial. La
> evidencia final con tests unitarios y escaneo de secretos está en
> `15-cierre-bloque6-p3.md`.

## Alcance

Cierre de los hallazgos bajos pendientes de la auditoría:

- BA-01: rollback que podía ocultar la excepción original.
- BA-03: parámetros de PBKDF2 y decisión de hardening.
- BA-04: XSS almacenado residual en el resumen del Dashboard.
- BA-05: actualizaciones/borrados sin validación de filas afectadas.
- BA-06: higiene de artefactos locales y temporales.

BA-02 se mantiene como fail-fast aceptado y documentado: HikariCP falla al arranque si la base no está disponible. No se modificaron claves, secretos ni variables de entorno.

## Cambios implementados

### BA-01 — Rollback preservando la causa

`src/com/CapaInfraestructura/Implementacion/JDBCTransaccion.java`

El fallo de `rollback()` se captura y se agrega como `Suppressed` a la excepción original. De este modo, un error de rollback ya no reemplaza la causa raíz.

### BA-03 — Decisión de hardening de PBKDF2

`src/com/CapaInfraestructura/Implementacion/AdaptadorContrasena.java`

Se mantiene PBKDF2-HMAC-SHA256 con 210.000 iteraciones y se agregó un comentario que documenta la decisión de hardening y la precaución de no reducir el costo sin revisión.

### BA-04 — Escape del resumen del Dashboard

`src/js/app.js`

El resumen de auditoría del Dashboard ahora escapa `log.accion`, `log.descripcion` y `log.fecha` mediante `escaparHtml` antes de interpolarlos en el HTML.

### BA-05 — Validación de filas afectadas

`src/com/CapaInfraestructura/Implementacion/MySqlRepositorioCliente.java`

Las operaciones de actualización y baja verifican `filasAfectadas == 0` y lanzan `RecursoNoEncontradoException`, que se traduce a HTTP 404.

`src/com/CapaInfraestructura/Implementacion/MySqlRepositorioProducto.java`

`eliminar` ahora verifica `filasAfectadas == 0` y lanza `RecursoNoEncontradoException` cuando el producto no existe o no pertenece al negocio.

### BA-06 — Higiene de artefactos locales

- `classpath.txt` dejó de estar trackeado y se agregó a `.gitignore`.
- Se eliminaron los archivos vacíos trackeados `src/com/CapaAplicacion/Service/a`, `src/com/CapaDominio/s` y `src/com/CapaInfraestructura/a`.
- Se eliminó el artefacto local `app.jfr`.
- No se modificaron `config.properties` ni ningún secreto local.

## Verificación

- Comando: `mvn test`
- Resultado: **345 tests, 0 fallos, 0 errores, 1 omitido**.
- Build: **BUILD SUCCESS**.
- Línea base: 216 tests; delta: +129 tests.
- Test omitido: `PasarelaMercadoPagoTest` (dependencia de entorno).
- `git diff --check`: sin errores de whitespace; solo advertencias de normalización LF/CRLF.
- `git ls-files`: `classpath.txt`, archivos vacíos y `app.jfr` ya no forman parte del conjunto trackeado.
- Los reportes de Surefire quedaron en `target/surefire-reports/`.

## Límites de la verificación

- No se ejecutaron pagos reales ni E2E contra sandbox de Stripe o Mercado Pago.
- La cobertura de pasarelas sigue siendo unitaria/integración con mocks o clientes falsos.
- No se agregaron tests específicos para BA-01, BA-04 y BA-05 en este bloque; la validación se apoyó en compilación y suite completa.
- El esquema MySQL inspeccionado no tiene `auditoria.id_negocio`; aplicar `migracion_auditoria_negocio.sql` antes de desplegar las consultas de auditoría multi-negocio.

## Veredicto

**APROBADO** para el alcance de remediación P3 (BA-01, BA-03, BA-04, BA-05 y BA-06), con las condiciones operativas anteriores documentadas como no bloqueantes.

El roadmap de evolución posterior está en `posibles_mejores_futuro.md`.
