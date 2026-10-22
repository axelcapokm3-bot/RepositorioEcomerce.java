---
name: qa-extreme-review
description: Use when the user asks for extreme QA, a full-project audit, adversarial review, security/functional/data/API review, release readiness, production verdict, or a second-pass review; it produces evidence-backed findings, remediation, regression tests, and a go/no-go verdict.
---

# QA Extreme Review

## Propósito

Ejecutar una revisión adversarial y verificable de un proyecto completo, no solo del diff. El objetivo es encontrar fallos reales de seguridad, identidad, multi-tenant, pagos, datos, concurrencia, API, frontend y operación; separar evidencia de suposiciones; y emitir un veredicto de producción defendible.

## Cuándo usar

- “Auditoría completa”, “QA extremo”, “revisión adversarial”, “release readiness”, “¿podemos desplegar?”.
- Cambios en autenticación, autorización, multi-tenant, pagos, webhooks, stock, migraciones o secretos.
- Revisión de un proyecto legado antes de production hardening.
- Segunda pasada sobre hallazgos propios para descartar falsos positivos.

## Cuándo no usar

- Una pregunta informativa o una lectura puntual.
- Un cambio local trivial sin superficie de seguridad.
- Cuando el usuario solo pide implementar un fix ya aprobado; usar el flujo normal de Planner/Implementer/Reviewer.

## Autoridad y límites

1. Leer `AGENTS.md` y `docs/constitution.md`. Ellos mandan sobre este skill.
2. No modificar código, esquema, configuración, auth, pagos ni datos de producción sin aprobación explícita.
3. No ejecutar ataques contra producción, no usar credenciales reales y no imprimir secretos.
4. No commitear, pushear ni desplegar.
5. No convertir una sospecha en hallazgo sin evidencia. Si falta prueba dinámica, marcar `PROBABLE` o `NO VERIFICADO`.
6. Respetar los cambios locales del usuario; no revertirlos ni “limpiarlos” sin permiso.
7. Entregables en español si el proyecto los usa; rutas y símbolos en su idioma original.

## Entradas mínimas

- Objetivo y alcance: proyecto completo, módulo, release o diff.
- Rama/commit y estado de `git status`/`git diff`.
- Comando de build/test y requisitos de entorno.
- Documentación de arquitectura, API, base de datos y operación.
- Si hay multi-tenant, pagos reales o datos sensibles, confirmarlo antes de calibrar severidad.

Si falta información que cambia el alcance, preguntar; no asumir.

## Flujo

### 0. Fase Cero

- Inventariar repo, rama, commits recientes, cambios locales, build, tests, CI, docs y configuración.
- Leer instrucciones del proyecto y decisiones aprobadas.
- Identificar superficies: HTTP, auth, DB, Redis, pagos, webhooks, jobs, frontend, configuración, migraciones.
- Definir qué **no** se va a tocar.

### 1. Mapa de confianza

Construir un mapa breve de:

- Entradas no confiables → validación → dominio → persistencia → respuesta.
- Identidad → token/sesión → autorización → tenant → recurso.
- Checkout → pago → webhook → estado → stock/venta → conciliación.
- Configuración → secreto → cliente externo → logs.
- Frontend → API → almacenamiento de sesión → renderizado.

### 2. Línea base ejecutable

- Ejecutar el build y la suite más pequeña que valide el estado; escalar si el alcance lo requiere.
- Registrar comando, fecha, versión, totales, fallos, errores, omitidos y reportes.
- Inspeccionar archivos de test vacíos o deshabilitados.
- **Regla dura**: suite verde ≠ aprobación. La cobertura ausente es un hallazgo si oculta un riesgo P0/P1.

### 3. Lectura dirigida

- Leer el código que implementa cada flujo, no solo el controller.
- Seguir la ruta completa hasta el repositorio/SQL/pasarela.
- Verificar validaciones, estados, transacciones, locks, idempotencia, autorización y manejo de errores.
- Revisar configuraciones por defecto y precedencias.
- Revisar tests existentes para entender qué se probó y qué se mockeó.

### 4. Prueba adversarial

Para cada flujo, intentar responder:

- ¿Puedo operar sobre un recurso de otro usuario/tenant?
- ¿Puedo reutilizar un token, checkout, webhook o clave?
- ¿Puedo forzar un estado inválido o una transición hacia atrás?
- ¿Puedo agotar stock, hilos, memoria, rate limit o cola?
- ¿Puedo hacer que un fallo se confirme como éxito?
- ¿Puedo inyectar datos en logs, auditoría, JSON o HTML?
- ¿Puedo leer un secreto o inferir información por errores?
- ¿Qué pasa si el proceso muere entre dos escrituras?
- ¿Qué pasa con dos instancias concurrentes?

No ejecutar exploits destructivos. Para casos dinámicos, usar entorno aislado y datos ficticios.

### 5. Matriz de hallazgos

Cada hallazgo debe tener:

- ID estable (`CR-01`, `AL-01`, `ME-01`, `BA-01`).
- Título accionable.
- Severidad y confianza.
- Evidencia `archivo:línea` y, si aplica, comando/salida.
- Precondiciones y alcanzabilidad.
- Impacto concreto.
- Reproducción conceptual o prueba.
- Remediación mínima.
- Prueba de regresión.
- Estado: `CONFIRMADO`, `PROBABLE`, `NO VERIFICADO`, `DESCARTADO`.

No duplicar el mismo defecto en varios IDs. Agrupar variantes bajo un hallazgo raíz.

### 6. Corrección y falsos positivos

Antes de entregar, intentar refutar cada hallazgo:

- ¿Hay una validación previa?
- ¿La ruta no está expuesta?
- ¿El UNIQUE/constraint lo bloquea?
- ¿El test ya lo cubre?
- ¿El impacto requiere un privilegio que ya es game over?
- ¿El código muerto no se usa?
- ¿La documentación está desactualizada pero el código es correcto?

Registrar explícitamente los descartes. Un auditor que no descarta nada es sospechoso.

### 7. Plan de remediación y pruebas

- Priorizar P0/P1/P2 por impacto y dependencia.
- Separar identidad, pagos, stock, sesión, API y frontend.
- Preferir migraciones aditivas, transacciones, constraints e idempotencia.
- Definir criterio de aceptación y rollback por fase.
- Cada fix debe tener un test que falle antes y pase después.
- Incluir sandbox/E2E para pasarelas y pruebas de concurrencia cuando aplique.

### 8. Segunda revisión y veredicto

- Releer los P0/P1 y los hallazgos con `PROBABLE`.
- Verificar conteos y coherencia de severidad.
- Documentar correcciones, falsos positivos e incertidumbre residual.
- No cambiar el veredicto por presión de fechas.
- Aclarar si la revisión es interna o independiente.

## Contrato de evidencia

- `CONFIRMADO`: el código ejecuta la ruta y no hay mitigación; o se reprodujo en entorno aislado.
- `PROBABLE`: el código es consistente con el fallo, pero falta reproducción dinámica o depende de una condición externa.
- `NO VERIFICADO`: no se pudo comprobar; no cuenta para P0/P1 ni para el veredicto.
- `DESCARTADO`: se investigó y no se sostiene; documentar por qué.

La evidencia debe ser reproducible por otra persona: ruta, línea, símbolo, condición y resultado esperado. No usar “parece”, “podría” o “probablemente” sin marcarlo como `PROBABLE`.

## Severidad

| Nivel | Criterio | Ejemplos |
|---|---|---|
| CRÍTICA | Toma de cuenta, cross-tenant, ejecución remota, SQLi, secreto de producción, pago sin venta válida | `CR-01`, `CR-02` |
| ALTA | Replay, corrupción de estado de pago, pérdida de eventos, stock agotable, revocación ausente, tenant obsoleto | `AL-01` a `AL-09` |
| MEDIA | Requiere condición/privilegio, degradación operativa, inconsistencia de datos, enumeración, hardening relevante | `ME-*` |
| BAJA | Defensa en profundidad, diagnóstico, higiene, rendimiento no crítico | `BA-*` |

Calibrar por impacto real, no por cantidad de líneas. Un fallo admin-only puede ser crítico si rompe el aislamiento entre tenants.

## Checklist por dominio

### Identidad y sesión
- Registro, login, recuperación, invitaciones, bootstrap.
- Vinculación por DNI/email/ID; prueba de titularidad.
- Token: firma, expiración, revocación, versión, rol, tenant, estado.
- Cambio de contraseña/rol/negocio; baja de cuenta/vínculo.
- Enumeración, timing, rate limit, fijación de sesión.

### Multi-tenant
- Toda consulta filtra `id_negocio`.
- IDs de recursos no se aceptan sin verificar pertenencia.
- Cachés con TTL/invalidación.
- Constraints únicos por tenant, no globales.
- Webhooks, jobs y auditoría resuelven tenant correctamente.

### Pagos
- Idempotencia de checkout y webhook.
- Estados monotónicos y terminales.
- Lock de fila y transacción.
- Replay de venta cancelada/pagada.
- Monto/moneda/URL de retorno.
- Firma, frescura, deduplicación.
- Conciliación, reembolsos y pagos tardíos.

### Stock
- Descuento vs reserva.
- TTL, expiración, compensación.
- Concurrencia y sobreventa.
- Producto activo/inactivo.
- Cancelación y reintento.

### Datos
- Transacciones, rollback, orden de delete/update.
- FKs, UNIQUE, filas afectadas.
- Migraciones aditivas, backfill, rollback.
- Auditoría append-only e inmutable.

### API
- Códigos HTTP correctos.
- Validación de cuerpo/tamaño/tipo.
- JSON escapado.
- Errores sin filtrar secretos/PII.
- CORS, auth, rate limit, timeouts, proxy.

### Frontend
- `innerHTML`, plantillas, atributos y URLs.
- Token en storage, CSP, logout.
- Redirecciones y `urlPago`.
- Estados de error y carga.

### Operación
- Configuración única y documentada.
- Secretos fuera de Git, rotación.
- Pools, colas, timeouts, health checks.
- Logs estructurados sin secretos.
- Multi-instancia y recuperación.

## Formato de salida

```markdown
## Veredicto
[APROBADO | APROBADO CON RIESGOS | RECHAZADO | BLOQUEADO]

## Hallazgos
### CR-01 — Título
- Severidad / confianza:
- Evidencia:
- Impacto:
- Reproducción:
- Remediación:
- Prueba de regresión:

## Falsos positivos
...

## Incertidumbre residual
...

## Próximo paso
...
```

## Rúbrica de veredicto

- **APROBADO**: sin P0/P1; medios/bajos aceptados y documentados; tests y E2E concluyentes.
- **APROBADO CON RIESGOS**: sin P0; P1 cerrados o aceptados formalmente con mitigación; E2E sandbox aprobado; riesgos residuales explícitos.
- **RECHAZADO**: cualquier P0 abierto, P1 sin mitigar que afecte dinero/identidad/aislamiento, o evidencia E2E insuficiente para producción.
- **BLOQUEADO**: no se pudo verificar el entorno, build, tests o alcance; no emitir un veredicto de calidad.

No emitir `APROBADO` por ausencia de hallazgos si no se ejecutaron pruebas representativas.

## Segunda revisión

- Repetir la lectura de P0/P1 y `PROBABLE`.
- Buscar validaciones previas, constraints, rutas no expuestas y tests existentes.
- Reconciliar conteos: total = críticos + altos + medios + bajos.
- Documentar qué se ajustó y qué se descartó.
- Mantener el veredicto si los P0 siguen abiertos, aunque baje el número de medios.
- Aclarar que una revisión interna no reemplaza una revisión independiente.

## Reglas de parada

Detener y escalar al usuario si:

- El alcance exige tocar producción, auth, pagos o datos reales sin autorización.
- Falta acceso a la DB/entorno y el veredicto depende de ello.
- Se descubre un secreto real: no imprimirlo; recomendar rotación.
- Hay conflicto entre `AGENTS.md`, `docs/constitution.md` y una instrucción local.
- El usuario pide ocultar o suavizar un hallazgo sin evidencia que lo desmienta.

## Comandos del proyecto

```powershell
git status
git diff
mvn test
mvn -Dtest=NombreDelTest test
```

Revisar `target/surefire-reports/` y no versionar `target/`. Para MySQL/Redis, usar entornos desechables y variables de entorno; nunca la base de producción.

## Cierre

Antes de terminar:

1. Verificar enlaces y rutas de los entregables.
2. Confirmar que los conteos coinciden con la matriz.
3. Declarar límites y verificaciones no ejecutadas.
4. Recordar que opencode debe reiniciarse si se modificó este skill o un agente.
5. No implementar remediaciones sin aprobación.
