# AGENTS.md — Guía para Agentes en el Proyecto eCommerce

Este documento describe cómo deben operar los agentes de IA dentro del proyecto eCommerce, asegurando que se adhieran a las convenciones, arquitectura y principios establecidos.

## 1. Contexto del Proyecto

- **Tipo de Proyecto**: Backend de eCommerce.
- **Lenguaje**: Java 21.
- **Gestor de Dependencias**: Maven.
- **Arquitectura**: Arquitectura por capas (CapaDominio, CapaAplicacion, CapaInfraestructura, CapaPresentacion, CapaExterna). La CapaDominio es el corazón y debe ser lo más independiente posible.
- **Framework Web**: Servidor HTTP nativo de Java SE (`jdk.httpserver`).
- **Persistencia**: MySQL (datos principales), Redis (carrito de compras con TTL), Repositorios en memoria (métodos de pago, datos volátiles).
- **Concurrencia**: Uso intensivo de `CompletableFuture` y `ThreadPoolExecutor` con configuraciones específicas (`ServiceExecutor`).
- **Manejo de JSON**: `com.fasterxml.jackson.core` para serialización interna/DTOs, pero `JsonUtils` personalizado para respuestas HTTP.
- **Integraciones de Pago**: Mercado Pago, Stripe.
- **Testing**: JUnit Jupiter, Mockito.
- **Documentación Existente**: `API_DOCUMENTATION.md`, `CONTEXTO.md`, `DocumentacionBaseDeDatos.txt`, `README.md`.
- **Reglas de Agentes Existentes**: Este documento (`AGENTS.md`) define las reglas.
- **Sistema SDD Existente**: Estructura `spec/NNN-nombre/` para especificaciones.
- **Herramientas Disponibles**: Maven, Git, terminal, herramientas estándar de Java/IDE, MySQL, Redis.

## 2. Principios Fundamentales del Agente

### 2.1. Regla Fundamental: Analizar Antes de Modificar

Antes de modificar, inspeccionar el objetivo, sus dependencias directas y el contexto suficiente para preservar el comportamiento. Leer `docs/constitution.md`; consultar las secciones pertinentes de `MEMORY.md`, especificaciones, documentación de API/base de datos, configuración y código cuando sean relevantes. No inspeccionar todo el repositorio ni releer documentación no relacionada por rutina.

Las reglas de autoridad, decisión, impacto, delegación, contexto y revisión tienen una única fuente canónica en `docs/constitution.md` y aplican a todos los agentes y subagentes.

## 3. Reglas de Generación de Código

Aplicar las convenciones de código centralizadas en `docs/constitution.md` y las convenciones existentes en el código pertinente. No introducir tecnologías o patrones ajenos al proyecto sin autorización del usuario.

## 4. Flujo de Trabajo y SDD (Spec-Driven Development)

Para cambios importantes o nuevas características, seguir el flujo:
`IDEA → EXPLORACIÓN → ANÁLISIS → PREGUNTAS → DECISIÓN DEL USUARIO → SPEC (spec/NNN-nombre/spec.md) → PLAN (spec/NNN-nombre/plan.md) → TASKS (spec/NNN-nombre/tasks.md) → IMPLEMENTACIÓN → TESTS → REVIEW → VALIDACIÓN → MEMORY`

El agente DEBE consultar y documentar en la estructura `spec/` cuando sea necesario, y obtener la aprobación del usuario antes de la implementación de decisiones de diseño importantes.

## 5. Memoria Persistente

Consultar solo las secciones de `MEMORY.md` relevantes para la tarea. Actualizarlo cuando una tarea produzca una decisión aprobada o un aprendizaje persistente; no registrar propuestas como decisiones.

## 6. Verificación

Toda tarea debe verificarse de forma proporcional a su impacto: revisar el diff y ejecutar la comprobación más pequeña que valide el comportamiento afectado. Cambios de código deben compilarse y probarse con los tests pertinentes; escalar a la suite completa cuando el alcance o el riesgo lo requiera. Cambios exclusivamente documentales no requieren Maven salvo que exista una validación documental aplicable.

El agente NUNCA debe considerar una tarea terminada hasta que haya sido verificada.

## 7. Jerarquía de Autoridad

La jerarquía única y vinculante está definida en `docs/constitution.md`. Si existe un conflicto real, detener el trabajo afectado y elevarlo al usuario; los planes, tareas e instrucciones locales no pueden ampliar el alcance aprobado.
