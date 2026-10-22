# Segunda revisión adversarial

## Método

Esta pasada se hizo como revisión interna del mismo auditor, no como auditoría independiente. Se releyeron los flujos y se intentó refutar cada hallazgo con estas preguntas:

1. ¿Existe una ruta HTTP/servicio alcanzable que llegue al código señalado?
2. ¿Hay una validación previa que bloquee el abuso?
3. ¿El impacto requiere una condición no demostrada?
4. ¿El defecto está en el código o solo en documentación/tests?
5. ¿La severidad es coherente con el resto de la matriz?
6. ¿La evidencia tiene archivo y línea verificables?
7. ¿El hallazgo se confunde con un control positivo existente?

No se ejecutaron ataques contra producción ni llamadas reales a pasarelas. La revisión es estática/dirigida y se apoya en la línea base de `mvn test` documentada en `04-pruebas.md`.

## Resultado global

| Resultado | Cantidad |
|---|---:|
| Hallazgos sostenidos sin cambios | 28 |
| Hallazgos ajustados en alcance o evidencia | 2 |
| Hallazgos descartados | 0 |
| Hallazgos nuevos que cambien el conteo | 0 |
| **Total** | **30** |

**Conteo final: 2 críticos + 9 altos + 13 medios + 6 bajos = 30.**

El veredicto **RECHAZADO para producción** no cambia: las correcciones afectan la precisión de algunos hallazgos, pero no eliminan ningún P0 ni reducen el riesgo de identidad/pagos.

## Resultado por hallazgo

| ID | Resultado | Ajuste o nota |
|---|---|---|
| CR-01 | SOSTENIDO | Explotable si el perfil víctima no tiene cuenta vinculada; si ya la tiene, el UNIQUE lo bloquea. Sigue siendo crítico. |
| CR-02 | SOSTENIDO | Requiere admin del tenant A y email de una cuenta existente; no requiere contraseña de la víctima. Sigue siendo crítico. |
| AL-01 | SOSTENIDO | La ruta pública exige `Idempotency-Key`, pero el replay del checkout existente ocurre después. |
| AL-02 | SOSTENIDO | La venta se bloquea con `FOR UPDATE`; el pago no. La degradación de `pago` tras `APROBADO` es posible. |
| AL-03 | SOSTENIDO | El ACK `200` antes del procesamiento es explícito en ambos controllers. |
| AL-04 | SOSTENIDO | El `return` temprano y el `true` ante fallo están confirmados en `WebhookService`. |
| AL-05 | SOSTENIDO | La compra directa no pasa por `CarritoService`, que sí valida activo. |
| AL-06 | SOSTENIDO | El token no consulta estado; el TTL máximo es una ventana, no una revocación. |
| AL-07 | AJUSTADO | En cache miss `ResolutorNegocio` **sí** valida activo. El defecto es el cache hit obsoleto y la falta de invalidación; se añadió que `registrar` no llama a `validarNegocioActivo`. |
| AL-08 | SOSTENIDO | No hay job de expiración/compensación; la cancelación manual no cubre abandono. |
| AL-09 | SOSTENIDO | El UNIQUE global está en la migración; la búsqueda por tenant no evita la colisión de inserción. |
| ME-01 | SOSTENIDO | La transacción única no existe; el 409 es enumerable. |
| ME-02 | SOSTENIDO | El token bootstrap no tiene marca de uso. |
| ME-03 | SOSTENIDO | El impacto es condicionado a datos controlados en el mensaje; se mantiene medio por defensa ausente. |
| ME-04 | SOSTENIDO | Se confirmó que usa solo `remoteAddress`; no hay proxy consciente. |
| ME-05 | SOSTENIDO | Las tres resoluciones tienen precedencias distintas. |
| ME-06 | SOSTENIDO | Es `PROBABLE`: no se probó una sesión Stripe real, pero falta la validación de URL. |
| ME-07 | SOSTENIDO | Es `PROBABLE`: no se probó replay real, pero no hay comparación de frescura. |
| ME-08 | SOSTENIDO | Es `PROBABLE` para la ruta pública sin transacción; el camino transaccional tiene rollback. |
| ME-09 | SOSTENIDO | Los servicios lanzan `RuntimeException`; el mapper HTTP devuelve 500. |
| ME-10 | SOSTENIDO | El POST es admin-only; el riesgo es poisoning por admin y la consulta errónea. |
| ME-11 | SOSTENIDO | El escaneo de secretos es real; el procesamiento posterior limita el impacto. |
| ME-12 | SOSTENIDO | La explotación no se midió con carga; el diseño sin timeouts es verificable. |
| ME-13 | SOSTENIDO | Los dos archivos Stripe siguen en 0 bytes y no hay E2E. |
| BA-01 | SOSTENIDO | El rollback dentro del catch puede ocultar la causa. |
| BA-02 | SOSTENIDO | Es fail-fast aceptable, pero sin diagnóstico estructurado. |
| BA-03 | SOSTENIDO | PBKDF2 210k no es un fallo explotable; es hardening. |
| BA-04 | SOSTENIDO | Solo el dashboard; la página de auditoría escapa. |
| BA-05 | SOSTENIDO | `Producto.actualizar` sí verifica filas; el defects se limita a clientes y `Producto.eliminar`. |
| BA-06 | SOSTENIDO | Documentación/artefactos y secreto local; no hay secretos MP/Stripe en el commit raíz. |

## Correcciones aplicadas a la matriz

1. **AL-07 se redujo a caché obsoleto.** La primera resolución de un negocio inactivo no se acepta: `ResolutorNegocio` comprueba `isActivo` y `fechaBaja` en cache miss (`:44`). El riesgo real es que una entrada ya cacheada no se invalide. Se añadió la falta de `validarNegocioActivo` en `registrar` como matiz del mismo problema de tenant.
2. **BA-05 se limitó a clientes y borrado de productos.** `MySqlRepositorioProducto.actualizar` sí verifica `filasActualizadas != 1`; no debe reportarse como update sin check.
3. **CR-01/CR-02 se condicionaron correctamente.** El UNIQUE `(id_negocio, id_cliente)` bloquea el takeover si el perfil ya está vinculado. La explotación existe sobre perfiles libres y sobre cuentas globales existentes, respectivamente. La severidad se mantiene porque el flujo normal crea perfiles desde backoffice y el ataque no requiere acceso previo a la víctima.

## Falsos positivos descartados explícitamente

- **SQL injection**: no se encontró en los repositorios revisados; hay `PreparedStatement`.
- **Falta de `Idempotency-Key` en la ruta pública**: `VentaController.java:115-126` la exige; la variante legacy no está expuesta.
- **Negocio inactivo aceptado en la primera resolución**: el resolutor sí lo rechaza; el problema es el caché.
- **Producto actualizado sin verificar filas**: el update sí verifica; solo el delete no.
- **`config.properties` empaquetado en el JAR**: `pom.xml:66-72` lo excluye.
- **Secretos MP/Stripe en el commit raíz**: el commit `3789908` solo contiene claves `db.*`.
- **JSON de error sin ningún escape**: `sendError` escapa comillas; el defecto es que no escapa backslash/control y no usa `JsonUtils`.
- **XSS en toda la página de auditoría**: la página completa escapa; el residual es el resumen del dashboard.
- **URL de Mercado Pago sin validación**: sí valida HTTPS/allowlist; el defecto de URL débil es específico de Stripe.
- **Auditoría manipulable por cualquier usuario**: el POST es admin-only; el poisoning requiere un admin comprometido.
- **Rollback siempre oculta la excepción**: solo si el propio rollback falla; es un defecto de diagnóstico, no de negocio.

## Nuevos matices detectados

- `AutenticacionService.registrar` no llama a `validarNegocioActivo`; la ruta HTTP pasa por `ResolutorNegocio`, pero el servicio no es seguro por sí mismo y un caché obsoleto permite el caso. Se documenta dentro de `AL-07`, no como hallazgo separado para no inflar el conteo.
- `existeVinculo` no filtra `activo`: un vínculo dado de baja sigue bloqueando una nueva vinculación y no se reutiliza correctamente. Se documenta como evidencia de `CR-01`.
- `listarNegocios` no filtra vínculos inactivos; es una inconsistencia de UX/estado, no un bypass de autorización. No se eleva a hallazgo independiente.
- La tabla de auditoría no tiene `id_negocio`; el filtro por joins oculta registros de entidades eliminadas. Se mantiene en `ME-10`.

## Pruebas de esta revisión

- Relectura dirigida de `AutenticacionService`, `PagoService`, `WebhookService`, `ResolutorNegocio`, `MySqlRepositorioCuentaNegocio`, `MySqlRepositorioProducto`, `BaseController` y migración de idempotencia.
- Verificación de la línea base: 41 suites, 216 tests, 0 fallos, 0 errores, 1 omitido.
- Verificación de archivos Stripe vacíos y de la ausencia de tests E2E.
- No se ejecutó un exploit dinámico, una prueba de carga ni una llamada sandbox durante esta pasada.

## Incertidumbre residual

- No se validó el esquema real de MySQL con `SHOW CREATE TABLE`; se usó `docs/seguridad-operativa.md` y las migraciones como evidencia. Si el esquema difiere, los hallazgos de constraints deben revalidarse.
- No se confirmó si el sistema opera con uno o varios tenants en producción; la severidad operativa de `CR-02`, `AL-04` y `AL-09` depende de ello, pero los defectos de código permanecen.
- No se confirmó si `config.properties` contiene credenciales reales; se recomienda rotarlas si hay duda.
- No se revisaron completos `API_DOCUMENTATION.md`, `spec/`, `.github/`, el diff completo ni el frontend restante; no se usaron para sostener P0/P1.
- La segunda revisión es interna, no independiente. Antes de un nuevo veredicto se requiere revisión humana/independiente y E2E sandbox.

## Conclusión

La segunda pasada **no descalifica** los dos críticos ni los altos de pagos, stock, sesión y tenant. Las correcciones mejoran la precisión y evitan afirmaciones falsas, pero el estado sigue siendo **RECHAZADO para producción**. El siguiente paso es aprobar el alcance de remediación (`05-decisiones-pendientes.md`) y ejecutar la Fase 1/2 con pruebas de regresión.
