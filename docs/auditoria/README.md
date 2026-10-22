# Auditoría técnica y de seguridad — eCommerce

Fecha de corte: **2026-10-21**  
Alcance: árbol de trabajo actual del repositorio `C:\Users\Axel\Programacion Java\eCommerce`  
Naturaleza: auditoría de solo lectura. **No se modificó código de producción, esquema, autenticación ni pagos.**

## Veredicto

**RECHAZADO para producción.**

El sistema tiene una base funcional real y una suite que pasa, pero no es seguro desplegarlo mientras existan los hallazgos críticos de identidad y los riesgos altos de pagos, stock y aislamiento multi-tenant. El veredicto no implica que el proyecto sea inservible: implica que **no debe exponerse a usuarios reales ni procesar dinero real** en el estado auditado.

Para desarrollo local controlado, la suite y los flujos principales son utilizables. Para producción, deben cerrarse primero los hallazgos `CR-*` y `AL-*` y añadirse las pruebas de regresión indicadas.

## Números de la auditoría

| Métrica | Resultado |
|---|---:|
| Hallazgos totales | 30 |
| Críticos | 2 |
| Altos | 9 |
| Medios | 13 |
| Bajos | 6 |
| Tests ejecutados | 216 |
| Fallos / errores | 0 / 0 |
| Omitidos | 1 (`PasarelaMercadoPagoTest`) |
| Archivos de reporte surefire | 41 |
| Integración MySQL | 2 tests, 0 fallos |
| Integración Redis | 4 tests, 0 fallos |
| Tests de Stripe webhook/pasarela | 0 (archivos de test vacíos) |

## Documentos

| Documento | Contenido |
|---|---|
| `01-resumen-ejecutivo.md` | Lectura para decisión: riesgo, impacto y condiciones para cambiar el veredicto. |
| `02-hallazgos.md` | Matriz detallada con evidencia, impacto, confianza y remediación. |
| `03-plan-remediacion.md` | Prioridades P0/P1/P2, criterios de aceptación y pruebas de regresión. |
| `04-pruebas.md` | Comandos, resultados, cobertura real y brechas de prueba. |
| `05-decisiones-pendientes.md` | Decisiones que requieren aprobación humana antes de implementar. |
| `06-segunda-revision.md` | Revisión adversarial de los propios hallazgos y correcciones de falsos positivos. |

## Cómo reproducir la evidencia

```powershell
mvn test
```

Resultado esperado en el estado auditado: `BUILD SUCCESS`, 216 tests, 0 fallos, 0 errores, 1 omitido.

La integración con MySQL y Redis requiere servicios locales disponibles; en esta auditoría `IdentidadGlobalMySqlTest` ejecutó 2 pruebas y `RedisCarritoRepositoryTest` ejecutó 4, sin fallos.

## Límites de esta auditoría

- No se ejecutaron pagos reales ni pruebas de sandbox contra Mercado Pago o Stripe.
- No hubo pruebas E2E de navegador, carga, estrés, recuperación ante desastres ni despliegue detrás de proxy.
- Las conclusiones sobre configuración de producción y cabeceras de proxy son análisis de código, no observación de un despliegue real.
- No se inspeccionaron valores secretos; solo se verificó su presencia y su ruta de carga.
- Los hallazgos marcados como `PROBABLE` requieren una prueba adicional en entorno controlado antes de convertirse en bloqueo definitivo.

## Regla de uso

Este directorio es evidencia de auditoría y un plan de trabajo. No es una autorización para modificar producción. Cualquier cambio en autenticación, pagos, esquema o contratos requiere aprobación explícita del usuario conforme a `AGENTS.md` y `docs/constitution.md`.
