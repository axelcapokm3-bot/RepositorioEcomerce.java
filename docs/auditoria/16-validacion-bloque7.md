# Validación Bloque 7 — Cierre y veredicto

**Fecha:** 2026-10-26
**Alcance:** validación final de la remediación y de los seis bloques cerrados.
**Commit bajo revisión:** `f84baec` (`fix: cierra auditoría P3 y consolida cambios pendientes`).
**Rama:** `appmod/java-upgrade-20260901194445`.
**Estado:** **BLOQUEADO — SIN VEREDICTO**.

## Resultado ejecutivo

No se emite veredicto **APROBADO**, **RECHAZADO** ni **CONDICIONADO**. Las
restricciones del bloque exigen E2E sandbox real y una revisión independiente
firmada, y ninguna de las dos está disponible. Además, el commit no está
mergeado a `main` y no quedó demostrado que haya 0 P0/P1 abiertos: CR-01 y CR-02
siguen con mitigación residual y las decisiones D-02/D-03 permanecen pendientes.

La suite completa en verde no sustituye la validación E2E de pagos ni la firma
independiente.

## Evidencia ejecutada

### Suite de regresión

| Comprobación | Resultado |
|---|---|
| `mvn test` | **398 tests, 0 fallos, 0 errores, 7 omitidos. BUILD SUCCESS** |
| Línea base (`04-pruebas.md`) | 216 tests, 0 fallos, 0 errores, 1 omitido |
| Delta | +182 tests, +6 omitidos |
| `node --test test/js/dashboard-escape.test.mjs` | 2 tests, 2 pass |
| `scripts/scan-secrets.ps1` | 0 hallazgos en 608 archivos trackeados |
| `git diff --check` | sin errores de whitespace |

Los 7 omitidos son:

- `PasarelaMercadoPagoTest`: 1 caso que depende de credenciales de entorno.
- `IdentidadGlobalMySqlTest`: 5 casos.
- `ComprasMultiNegocioMySqlTest`: 1 caso.

La corrida registró `No se encontró config.properties; configure las propiedades
mediante variables de entorno o system properties.` Por eso los 6 tests de
integración MySQL no se ejecutaron en esta validación. El documento
`10-cierre-bloque3.md:82-85` afirma que esa integración estuvo verde en una
corrida anterior (295 tests, 1 omitido), pero ese resultado no se reprodujo en
el entorno actual.

### E2E sandbox Stripe/Mercado Pago

**No ejecutado.** No se encontró un harness, script o suite E2E en el
repositorio. La cobertura de pasarelas sigue siendo unitaria o con dobles:

- `PasarelaStripeTest` inyecta un `CreadorSesion` falso.
- `StripeWebhookControllerTest` y `StripeWebhookServiceTest` usan firmas HMAC o
  payloads locales.
- `PasarelaMercadoPagoTest` deja 1 caso omitido por entorno.
- Las migraciones y el flujo completo de `04-pruebas.md:139-155` no se
  ejecutaron.

No están definidas en el entorno `MERCADOPAGO_ACCESS_TOKEN`,
`MERCADOPAGO_WEBHOOK_SECRET`, `STRIPE_SECRET_KEY` ni `STRIPE_WEBHOOK_SECRET`.
Existen `bin/config.properties` y `src/main/resources/config.properties`, ambos
ignorados por Git. El segundo contiene claves de pago, pero su origen no está
verificado y no se usó ni se imprimió. No hay evidencia segura de que sean
credenciales sandbox.

### Revisión independiente

**No firmada.** `06-segunda-revision.md:106` ya había dejado constancia de que la
segunda revisión fue interna y de que se requiere revisión humana/independiente
y E2E sandbox antes de un nuevo veredicto. No hay firma independiente en el
repositorio. La revisión técnica de este informe fue realizada por el agente de
QA en esta sesión, pero no es una firma humana ni una revisión independiente.

### Falsos positivos

Se verificó que la remediación no los reabrió:

| Falso positivo | Verificación |
|---|---|
| SQL injection | Los repositorios revisados usan `PreparedStatement`; no se encontró concatenación de entrada. |
| Falta de `Idempotency-Key` | `VentaController.java:145-156` la exige y valida longitud. |
| Negocio inactivo aceptado en primera resolución | `ResolutorNegocio.java:83-93` exige `estaOperativo()` en cache miss; el residual es el TTL/invalidación de caché (AL-07). |
| Producto actualizado sin verificar filas | `MySqlRepositorioProducto.java:155` valida filas; el delete también (`:174`). |
| `config.properties` empaquetado en el JAR | `pom.xml:66-72` excluye el archivo de resources. |
| JSON de error sin escape | `JsonUtils.java:88-108` escapa comillas, backslash y caracteres de control. |
| XSS en toda la página de auditoría | `app.js:993-996` escapa los campos; el dashboard también (`app.js:346-349`). |
| URL de Mercado Pago sin validación | `PasarelaMercadoPago.java:214-258` exige HTTPS, host público y rechaza localhost. |
| Auditoría manipulable por cualquier usuario | `AuditoriaController.java:38-67` exige autenticación y rol ADMIN. |
| Rollback siempre oculta la excepción | `JDBCTransaccion.java:37-45` conserva la causa y agrega el fallo de rollback como `Suppressed`. |
| Secretos MP/Stripe en el commit raíz | El escaneo actual no encontró secretos en 608 archivos trackeados. |

### P0/P1 y estado de integración

`docs/auditoria/07-cierre-cr01-cr02.md:35-41` reconoce que la validación de
identidad es más débil que la invitación por token y que la mitigación completa
de CR-01/CR-02 sigue pendiente. `05-decisiones-pendientes.md:19-39` mantiene
D-02 y D-03 pendientes. Por lo tanto, no puede afirmarse sin reservas que haya
0 P0/P1 abiertos; CR-01/CR-02 están mitigados, no cerrados en el sentido fuerte
de la auditoría.

Los hallazgos AL-01 a AL-09 se documentan como cerrados en
`08-cierre-pagos-webhooks.md`, `09-cierre-al05-al09.md` y
`10-cierre-bloque3.md`, pero la validación actual no volvió a ejecutar los 6
tests MySQL que respaldan esa afirmación.

### PRs mergeados

El commit `f84baec` está publicado en la rama
`appmod/java-upgrade-20260901194445`, pero no está mergeado a `main`:

- `origin/main`: `2d2da8f`.
- `HEAD` no es ancestro de `origin/main`.
- `git rev-list --count origin/main..HEAD`: **7 commits**.

La entrada "PRs mergeados" no se cumple en el estado actual.

## Hallazgos de la validación

| ID | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| B7-01 | **Bloqueante** | No hay E2E sandbox real de Stripe/Mercado Pago. | Sin harness E2E; tests con mocks; `04-pruebas.md:139-155`. |
| B7-02 | **Bloqueante** | No hay revisión independiente firmada. | `06-segunda-revision.md:106`; no hay firma en el repo. |
| B7-03 | **Bloqueante** | El commit bajo revisión no está mergeado a `main`. | `HEAD` no es ancestro de `origin/main`; 7 commits de diferencia. |
| B7-04 | **Alta** | No está demostrado que haya 0 P0/P1 abiertos. | CR-01/CR-02 con residual; D-02/D-03 pendientes (`05:19-39`, `07:35-41`). |
| B7-05 | **Media** | Los tests de integración MySQL no se ejecutaron en esta corrida. | 6 omitidos por falta de `config.properties` en classpath. |
| B7-06 | **Media** | Los documentos de cierre se contradicen y uno declara APROBADO sin E2E. | `05:154-178` (345/1, sin E2E), `13:59-69` (345/1), `15:147-161` (398/7). |
| B7-07 | **Media** | El origen de las credenciales locales de pago no está verificado. | `src/main/resources/config.properties` ignorado y con claves MP/Stripe; rotación pendiente. |

## Condiciones para emitir veredicto

1. Ejecutar la matriz E2E de `04-pruebas.md` con MySQL/Redis desechables y
   credenciales sandbox verificadas, documentando comando, resultado y logs sin
   secretos.
2. Obtener una revisión independiente humana firmada sobre el commit exacto que
   se pretende aprobar.
3. Mergear la PR o confirmar formalmente el commit bajo revisión.
4. Cerrar o aceptar formalmente el residual de CR-01/CR-02 y las decisiones
   D-02/D-03; demostrar 0 P0/P1 abiertos con esa aceptación.
5. Corregir las contradicciones de `05`, `13` y `15`; el "Cierre final Bloque 7"
   de `05` no cumple los criterios actuales.

## Veredicto

**SIN VEREDICTO.** No corresponde emitir APROBADO, RECHAZADO ni CONDICIONADO
mientras falten E2E sandbox y revisión independiente firmada, y mientras el
commit no esté mergeado y no se haya demostrado el cierre de P0/P1.

## Firma

Revisión técnica: agente de QA de openCode, 2026-10-26. No es una firma humana y
no sustituye la revisión independiente exigida.
