# MEMORY.md — eCommerce Project

## Estado actual
El proyecto ha sido analizado para establecer la infraestructura inicial de agentes. Se han creado `docs/constitution.md` y `AGENTS.md`.

## Decisiones
- Este apartado conserva decisiones ya registradas en el proyecto; la memoria no prueba por sí misma quién las aprobó ni autoriza cambios. No inferir autor, alternativas ni fecha histórica que no estén documentados.
- Se adoptó una arquitectura de agentes adaptada al proyecto Java 21, Maven y arquitectura por capas existente.
- Mercado Pago usa exclusivamente Orders API, sin Preferences, `init_point` ni `merchant_order`; la Order provee `checkout_url` y su ID se persiste (aprobado por el usuario, 2026-10-08).
- Crear ventas exige `Idempotency-Key`; la clave de solicitud se persiste en la venta y se valida contra el contenido de reintentos. La clave estable enviada a la pasarela se persiste por separado en el pago y se reutiliza al reintentar (aprobado por el usuario, 2026-10-08).
- El checkout de Mercado Pago crea Orders `online` con `processing_mode=manual`. La confirmación de pagos en efectivo y transferencia sigue siendo interna, sin endpoint público (aprobado por el usuario, 2026-10-08).
- Se establecieron principios estrictos para la generación de código (no lambdas por defecto, nombres en español para el dominio, no sobre-ingeniería).
- Se definió una jerarquía de autoridad clara para la toma de decisiones.
- Se adoptó el protocolo estricto de autoridad del usuario para todos los agentes y subagentes; las decisiones no definidas deben consultarse y no pueden resolverse mediante suposiciones.

Para nuevas decisiones aprobadas, registrar contexto, alternativas relevantes, decisión explícita del usuario, estado y fecha. Las propuestas pendientes no se registran como decisiones.

## Razones de las decisiones
- **Adaptación**: Maximizar la compatibilidad con el código base existente y las convenciones del proyecto para facilitar la integración y reducir la fricción.
- **Control Humano**: Asegurar que el usuario mantenga la autoridad final sobre las decisiones arquitectónicas y de negocio.
- **Calidad del Código**: Fomentar prácticas de Clean Code y mantenibilidad.
- **Transparencia**: Documentar explícitamente las reglas y decisiones para un entendimiento común.

## Aprendizajes
- El proyecto utiliza un servidor HTTP nativo de Java SE, no un framework web como Spring Boot.
- Se usan `CompletableFuture` y `ThreadPoolExecutor` para concurrencia.
- La serialización JSON para respuestas HTTP usa `JsonUtils` personalizado, no Jackson directamente.
- Existen varias "caveats" e inconsistencias en la API y el manejo de excepciones que deben ser corregidas en el futuro.

## Errores a evitar
- Imponer frameworks o arquitecturas externas sin un análisis previo.
- Tomar decisiones arquitectónicas sin la aprobación explícita del usuario.
- Generar código que no cumpla con las reglas de nomenclatura o el principio de "no lambdas".
- Sobrescribir archivos existentes sin análisis.

## Próximos pasos
No hay próximos pasos de modernización de agentes registrados.

## Comandos
- `mvn clean package`: Compilar el proyecto.
- `mvn test`: Ejecutar los tests.
- `mvn exec:java -Dexec.mainClass=com.CapaExterna.ServerMain`: Ejecutar la aplicación principal.

## Reglas específicas
- Los problemas conocidos son observaciones y no autorizan cambios por sí solos.
- Las excepciones de dominio (`BusinessException`) actualmente caen en el `500` genérico; esto debe ser mejorado.
- El campo `cuit` en `Cliente` no se persiste.
- El tipo de pago `TARJETA_CREDITO` no tiene una pasarela implementada.
- Los métodos de pago se almacenan en memoria y se pierden al reiniciar.

## Tareas completadas
- Creación de `docs/constitution.md`.
- Creación de `AGENTS.md`.
- Incorporación del protocolo estricto de autoridad del usuario en la configuración global y en todos los perfiles de agentes OpenCode.
- Centralización de reglas globales de agentes y optimización de contexto, delegación, impacto y revisión.
