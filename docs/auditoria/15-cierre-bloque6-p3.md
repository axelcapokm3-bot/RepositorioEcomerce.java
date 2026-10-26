# Cierre Bloque 6 — P3 Hardening (BA-01 a BA-06)

## Alcance

Cierre de los seis hallazgos bajos de la auditoría sin modificar hallazgos
P0/P1 ni reabrir falsos positivos:

- **BA-01**: el fallo de rollback no debe ocultar la causa original.
- **BA-02**: diagnóstico estructurado en el arranque fail-fast.
- **BA-03**: PBKDF2 con 210.000 iteraciones es una decisión de hardening, no un
  bug.
- **BA-04**: el resumen del dashboard debe escapar los datos de auditoría.
- **BA-05**: clientes y `Producto.eliminar` deben verificar filas afectadas.
- **BA-06**: higiene de artefactos locales y revisión de secretos.

## Decisiones y cambios por hallazgo

### BA-01 — Rollback preservando la causa

`JDBCTransaccion` captura el fallo de `rollback()` y lo agrega como
`Suppressed` a la excepción original. Si la excepción original no es de
negocio, se envuelve conservándola como `cause`; si es `BusinessException`, se
relanza la misma instancia con el fallo de rollback suprimido. Para poder
probarlo sin MySQL se agregó un constructor package-private con
`ProveedorConexion`, siguiendo el patrón ya usado por `MySqlRepositorioPago`.

Test: `test/java/com/CapaInfraestructura/Implementacion/JDBCTransaccionTest.java`
(3 casos: rollback fallido, `BusinessException` y commit exitoso).

### BA-02 — Diagnóstico estructurado de arranque

`DiagnosticoArranque` centraliza una línea saneada con formato:

```text
event=startup.fail component=<componente> error=<tipo>: <mensaje>
```

`DatabaseConnection` registra el evento y conserva la causa original dentro de
`ExceptionInInitializerError`. `ServerMain` registra el fallo de arranque y
termina con código de salida `1`. Se mantiene el fail-fast: no se agregan
health check, reintentos ni inicialización perezosa en este bloque.

Test: `test/java/com/CapaInfraestructura/Configuracion/DiagnosticoArranqueTest.java`
(3 casos: formato, saneamiento de credenciales/saltos de línea y salida a
stderr). La suite mostró la línea real al omitir la integración MySQL:

```text
event=startup.fail component=DatabaseConnection error=IllegalStateException: Falta la configuración 'db.url'...
```

### BA-03 — PBKDF2 210k como decisión de hardening

Se mantiene **PBKDF2-HMAC-SHA256 con 210.000 iteraciones**, sal de 16 bytes y
hash de 256 bits. Se documenta como decisión de hardening (no como bug) en
`docs/seguridad-operativa.md` §8, incluidas las alternativas evaluadas y no
adoptadas en este bloque: Argon2id, subir a ≥ 600.000 iteraciones y poner un
tope a las iteraciones leídas de la base. Cualquier cambio futuro requiere una
decisión nueva y migración transparente al iniciar sesión.

Test: `AdaptadorContrasenaTest.mantieneLosParametrosDeHardeningDocumentados`
verifica prefijo, iteraciones, longitud de sal y longitud de hash.

### BA-04 — Escape del resumen del dashboard

`src/js/app.js` escapa `log.accion`, `log.descripcion` y `log.fecha` con
`escaparHtml` antes de interpolarlos en el HTML del dashboard. La página de
auditoría completa ya escapaba sus campos y no se modificó.

Test: `test/js/dashboard-escape.test.mjs` (Node, 2 casos). Extrae la función
real `escaparHtml` de `app.js`, verifica que neutraliza un payload con etiqueta
y atributos, y que el bloque `renderDashboard` usa las tres interpolaciones
escapadas y ninguna de las versiones sin escapar.

### BA-05 — Validación de filas afectadas

`MySqlRepositorioCliente.actualizar` y `eliminar` usan `ValidadorFilas` y
lanzan `RecursoNoEncontradoException` cuando no afectan filas.
`MySqlRepositorioProducto.eliminar` aplica el mismo control. La actualización
de producto ya verificaba filas y se mantiene sin cambios.

Tests:

- `MySqlRepositorioClienteTest` (4): actualización y baja con 0 filas fallan,
  con 1 fila no fallan.
- `MySqlRepositorioProductoTest` (2): eliminación con 0 filas falla, con 1 fila
  no falla.

Para el cliente se agregó un constructor package-private con
`ProveedorConexion` (mismo patrón que `MySqlRepositorioPago`) que permite probar
sin MySQL.

### BA-06 — Higiene de artefactos y secretos

- `classpath.txt` dejó de estar trackeado y está en `.gitignore`.
- Los archivos vacíos trackeados (`src/com/CapaAplicacion/Service/a`,
  `src/com/CapaDominio/s`, `src/com/CapaInfraestructura/a`) y `app.jfr` ya no
  forman parte del repositorio. `*.jfr` está ignorado.
- `config.properties` en la raíz, `src/main/resources` y `bin/` está ignorado y
  excluido del artefacto Maven. Se corrigió la instrucción de
  `config.properties.example` para reflejar la ubicación real en la raíz.
- Se corrigió en `API_DOCUMENTATION.md` la allowlist CORS por defecto para
  incluir `http://127.0.0.1:5500` y `http://127.0.0.1:8080`, que ya estaban en
  `BaseController`.
- `scripts/scan-secrets.ps1`: **sin hallazgos en 537 archivos trackeados**
  (2026-10-26).

Los archivos locales de configuración contienen valores no-placeholder. No se
imprimieron ni se modificaron. **Hay duda razonable sobre su origen**: si alguna
vez fueron credenciales reales, deben rotarse según
`docs/seguridad-operativa.md` §2 y §3. La rotación externa queda pendiente del
operador porque requiere acceso a MySQL, Mercado Pago y Stripe.

## Archivos modificados

### Código

- `src/com/CapaInfraestructura/Implementacion/JDBCTransaccion.java`
- `src/com/CapaInfraestructura/Implementacion/DatabaseConnection.java`
- `src/com/CapaInfraestructura/Configuracion/DiagnosticoArranque.java` (nuevo)
- `src/com/CapaExterna/ServerMain.java`
- `src/com/CapaInfraestructura/Implementacion/MySqlRepositorioCliente.java`
- `src/js/app.js` (fix de BA-04 ya presente en el bloque)
- `src/com/CapaInfraestructura/Implementacion/AdaptadorContrasena.java`
  (comentario de decisión de BA-03)

### Tests

- `test/java/com/CapaInfraestructura/Implementacion/JDBCTransaccionTest.java`
- `test/java/com/CapaInfraestructura/Configuracion/DiagnosticoArranqueTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/MySqlRepositorioClienteTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/MySqlRepositorioProductoTest.java`
- `test/java/com/CapaInfraestructura/Implementacion/AdaptadorContrasenaTest.java`
- `test/js/dashboard-escape.test.mjs`

### Documentación

- `docs/seguridad-operativa.md` (§8, §9 y §10)
- `API_DOCUMENTATION.md` (allowlist CORS)
- `config.properties.example` (ubicación del archivo local)
- `docs/auditoria/13-cierre-p3.md` (remite a este documento)
- `MEMORY.md`

## Verificación

| Comprobación | Resultado |
|---|---|
| `mvn test` | **398 tests, 0 fallos, 0 errores, 7 omitidos. BUILD SUCCESS** |
| `node --test test/js/dashboard-escape.test.mjs` | **2 tests, 0 fallos** |
| `scripts/scan-secrets.ps1` | **0 hallazgos en 537 archivos trackeados** |
| `git ls-files` | sin `classpath.txt`, archivos vacíos ni `app.jfr` |
| `git check-ignore` | `config.properties`, `bin/`, `classpath.txt` y `*.jfr` ignorados |
| `git diff --check` | sin errores de whitespace |

Omitidos: `PasarelaMercadoPagoTest` (dependencia de entorno), 5 casos de
`IdentidadGlobalMySqlTest` y 1 de `ComprasMultiNegocioMySqlTest` (sin
`config.properties` en el classpath durante la corrida). Los tests unitarios de
este bloque no dependen de MySQL.

## Límites

- No se ejecutaron pagos reales ni E2E contra Stripe o Mercado Pago.
- BA-02 mantiene fail-fast sin health check ni reintento; queda como evolución.
- BA-03 no migra a Argon2id ni sube el costo de PBKDF2 en este bloque.
- BA-06 no rota credenciales externas; deja la acción operativa documentada.
- El test de dashboard usa Node y no está integrado en Maven; el comando queda
  documentado para CI o ejecución local.

## Veredicto

**APROBADO** para el alcance P3 (BA-01, BA-02, BA-03, BA-04, BA-05 y BA-06).
Cada hallazgo tiene test o decisión documentada. No se tocaron hallazgos P0/P1.
