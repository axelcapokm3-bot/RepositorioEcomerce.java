## 1. Arquitectura por Capas y Desacoplamiento (Basado en Capas Existentes)

El proyecto sigue una arquitectura por capas estricta para promover la separación de responsabilidades y el desacoplamiento:

- **CapaDominio**: El núcleo del sistema. Contiene entidades, enums, excepciones de negocio, estrategias y eventos de dominio. Es completamente independiente de frameworks, bases de datos, APIs o detalles de infraestructura. NINGUNA otra capa puede ser una dependencia a la CapaDominio. La CapaDominio NO debe depender de ninguna otra capa.

- **CapaAplicacion**: Orquesta las operaciones del dominio. Contiene servicios, DTOs y mappers. Define interfaces para los repositorios y pasarelas de pago, que son implementadas en la CapaInfraestructura. Depende de la CapaDominio y de interfaces definidas por el dominio o la propia CapaAplicacion.

- **CapaInfraestructura**: Contiene las implementaciones de los contratos definidos por la CapaAplicacion (repositorios MySQL/Redis, EventBus, pasarelas de pago externas, configuración de concurrencia y transacciones). Es una capa de detalles, que depende de la CapaAplicacion y CapaDominio.

- **CapaPresentacion**: Contiene los controladores HTTP que exponen la API REST. Adapta las peticiones del exterior a las operaciones de la CapaAplicacion y transforma los resultados del dominio a DTOs de respuesta. Depende de la CapaAplicacion.

- **CapaExterna**: El punto de entrada de la aplicación (`ServerMain`). Se encarga de la configuración y el arranque de la aplicación, inyectando las dependencias.

**Principios clave:**

- **Independencia del Dominio**: La lógica de negocio fundamental en `CapaDominio` debe ser independiente de frameworks, bases de datos, UI y servicios externos.
- **Desacoplamiento**: Minimizar las dependencias directas entre capas y entre componentes de la misma capa. Preferir la inversión de dependencias a través de interfaces.
- **Responsabilidad Única**: Cada clase, módulo o función debe tener una única razón para cambiar.

## 2. Clean Code y Mantenibilidad

Priorizar la calidad del código para asegurar su mantenibilidad, legibilidad y adaptabilidad a largo plazo.

- **Claridad y Legibilidad**: El código debe ser fácil de entender por otros desarrolladores.
- **Simplicidad**: Buscar siempre la solución más simple que resuelva el problema. Evitar la complejidad innecesaria.
- **Nombres Descriptivos**: Utilizar nombres que revelen la intención de variables, métodos, clases y paquetes.
- **Funciones y Métodos Pequeños**: Cada función debe tener una responsabilidad clara y limitada.
- **Alta Cohesión, Bajo Acoplamiento**: Los elementos relacionados deben estar juntos; los no relacionados, separados.
- **Evitar Duplicación**: Siempre que sea posible, evitar la duplicación de lógica.

## 3. Nomenclatura en Español (Dominio y Lógica de Negocio)

Cuando sea compatible con la tecnología y las convenciones del proyecto, se utilizarán nombres en español para:

- Variables, métodos, funciones, clases, interfaces, parámetros dentro de las capas de Dominio y Aplicación, y en lógica de negocio.
- Entidades, casos de uso, DTOs y componentes propios del dominio (ej: `calcularTotal`, `buscarCliente`, `crearPedido`, `ProductoService`).

Las tecnologías y convenciones externas (frameworks, librerías, estándares como HTTP, JSON, API, DTO, Controller, Repository, Service) conservarán sus nombres originales en inglés.

## 4. Prohibición de Lambdas (Salvo Excepción Justificada)

Se prohíbe el uso de expresiones lambda, a menos que:

- Sean absolutamente imprescindibles por una API o framework existente (ej: `CompletableFuture`, `Stream` API si su uso es un patrón establecido).
- El proyecto existente ya dependa extensivamente de ellas.
- Su eliminación empeore claramente la claridad o concisión del código.
- El usuario las autorice explícitamente tras justificación.

Se priorizará siempre un código explícito, fácil de leer y depurar, incluso si esto implica mayor número de líneas. No se sacrificará la comprensión por la concisión.

## 5. No Sobre-Ingeniería

No se crearán automáticamente:

- Interfaces innecesarias o vacías.
- Abstracciones sin un propósito claro.
- Clases o capas vacías.
- Microservicios o patrones de diseño que no resuelvan un problema real en el contexto actual del proyecto.

La arquitectura debe ser robusta, pero no artificialmente compleja.

## 6. Concurrencia y Asincronía

El proyecto utiliza `CompletableFuture` y `ThreadPoolExecutor` para gestionar la concurrencia y asincronía. Las operaciones de servicio deben retornar `CompletableFuture` y los controladores deben gestionar su resolución y manejo de errores. Se respetarán las configuraciones de `ServiceExecutor` y `RegistroExecutors` existentes.

## 7. Manejo de Errores

Se seguirá el formato de errores documentado en `API_DOCUMENTATION.md`. Es fundamental que las excepciones de dominio (`BusinessException`) se manejen explícitamente en la `CapaAplicacion` o `CapaPresentacion` para retornar códigos HTTP y mensajes de error apropiados, en lugar de caer en el `500` genérico (este es un problema conocido que debe abordarse).

## 8. Memoria Persistente

Las decisiones explícitamente aprobadas por el usuario y los aprendizajes pertinentes deben documentarse en `MEMORY.md`. La memoria preserva contexto, pero no sustituye una especificación ni constituye por sí sola autorización para ejecutar cambios. Los problemas conocidos y próximos pasos son información, no órdenes de implementación.

## 9. Arquitectura Evolutiva

La arquitectura puede y debe evolucionar para adaptarse a nuevas necesidades, pero los cambios arquitectónicos significativos requieren análisis, justificación y aprobación explícita del usuario.

## 10. Prioridad de Reglas

1. Decisiones explícitas del usuario.
2. Esta `constitution.md`.
3. `AGENTS.md`.
4. Especificaciones (`spec.md`) aprobadas por el usuario.
5. Decisiones explícitas tomadas durante el proyecto y registradas en `MEMORY.md` u otra documentación oficial.
6. Workflows, planes y tareas aprobados, siempre dentro del alcance autorizado.
7. Instrucciones de agentes especializados.
8. Inferencias y sugerencias del agente.

Ningún nivel inferior puede contradecir o modificar unilateralmente uno superior. Si hay un conflicto real, se detiene el trabajo afectado y se consulta al usuario.

## 11. Autoridad del Usuario y Decisiones

El usuario es la autoridad final sobre arquitectura, alcance, requisitos, reglas de negocio, tecnologías, patrones, estructura, responsabilidades, comportamiento, restricciones, excepciones y prioridades.

Los agentes pueden analizar, detectar problemas, explicar consecuencias, comparar alternativas, recomendar, verificar y revisar. No pueden convertir una recomendación en una decisión, inventar o cambiar requisitos, ampliar o reducir el alcance, ni alterar decisiones aprobadas.

Una solicitud explícita autoriza únicamente el trabajo y los efectos expresamente descritos. La autorización no cubre decisiones importantes pendientes ni efectos adicionales no especificados. Para trabajo CRITICAL, la solicitud explícita basta para el alcance pedido; no se solicita una confirmación ceremonial adicional. Si el trabajo requiere una decisión no definida o un efecto fuera de ese alcance, se detiene esa parte y se consulta al usuario.

Las decisiones importantes deben quedar identificables con:

- decisión pendiente o aprobada;
- contexto;
- alternativas relevantes;
- decisión explícita del usuario;
- fecha y estado.

Una decisión aprobada es una restricción hasta que el usuario la cambie. No se deben completar registros históricos con datos o aprobaciones que no consten en el proyecto.

## 12. Resolución de Ambigüedades y Bloqueos

Antes de preguntar, el agente debe comprobar las instrucciones explícitas, decisiones registradas, esta constitución, `AGENTS.md`, las especificaciones aplicables, el código pertinente y las convenciones establecidas.

Solo se bloquea cuando falta una decisión necesaria, existe una contradicción material o hay más de una interpretación razonable que cambie el comportamiento, el alcance, la arquitectura, la seguridad o los datos. El agente informa qué falta, por qué importa y las alternativas relevantes, y espera la decisión.

No se pregunta por detalles triviales ya resueltos por una convención existente ni se vuelve a preguntar algo que ya está explícitamente definido. No asumir sigue siendo obligatorio: una convención existente se aplica solo cuando resulte pertinente y no contradiga una instrucción.

## 13. Impacto, Flujo y Revisión Proporcionales

Clasificar el impacto del cambio por riesgo y alcance:

- **LOW**: cambio local y acotado sin efectos transversales, contractuales o arquitectónicos. Flujo corto; validar el diff y el comportamiento pertinente.
- **MEDIUM**: cambio que afecta varios componentes sin alterar decisiones fundamentales. Analizar el alcance, implementar y hacer revisión localizada.
- **HIGH**: cambio de arquitectura, dominio, persistencia o contratos. Requiere decisiones y plan aprobados antes de implementar, además de revisión profunda.
- **CRITICAL**: seguridad, autenticación, pagos, datos, operaciones destructivas o decisiones fundamentales. Aplicar bloqueo a decisiones o efectos no autorizados y realizar revisión exhaustiva. La solicitud explícita autoriza solamente su alcance descrito.

La profundidad de análisis, pruebas y revisión sigue el riesgo real. No omitir una validación que aporte una garantía distinta; evitar repetir la misma revisión. Para cambios LOW, una comprobación focalizada del implementador puede bastar; incorporar un reviewer separado cuando el riesgo, la solicitud o una dimensión independiente lo justifique.

## 14. Roles, Delegación y Comunicación

Cada agente tiene una responsabilidad principal:

- **Coordinator**: controla el flujo, identifica bloqueos y decide si una delegación especializada es necesaria. No diseña arquitectura ni implementa.
- **Explore**: recopila evidencia acotada y de solo lectura; no decide ni delega.
- **Planner**: transforma requisitos y decisiones aprobados en un plan; no completa decisiones pendientes.
- **Implementer**: ejecuta el alcance aprobado y verifica los cambios; no diseña silenciosamente.
- **Reviewer**: revisa el cambio y su evidencia de forma proporcional al impacto; no repite análisis global sin motivo.
- **General**: resuelve directamente tareas que no requieren una especialización existente; no coordina el flujo ni delega.

Delegar solo cuando una responsabilidad especializada aporte evidencia o trabajo distinto que no sea eficiente resolver directamente. No crear ni invocar agentes por costumbre. La delegación no transfiere autoridad: ningún subagente puede redefinir el alcance, decidir por el usuario o delegar recursivamente.

Cada encargo delegado debe contener solo el objetivo, límites, decisiones aprobadas, archivos pertinentes, resultado esperado y validación necesaria. El agente devuelve resultados y bloqueos, no decisiones no autorizadas. Compartir contexto adicional únicamente cuando sea necesario para la corrección.

## 15. Contexto y Fuentes

Usar el contexto mínimo suficiente: instrucciones globales, contexto específico de la tarea y archivos directamente relacionados. No leer automáticamente todo el proyecto, todas las especificaciones, toda la memoria ni el historial. Consultar documentos adicionales cuando el cambio dependa de ellos; no reducir el contexto necesario para ahorrar tokens.

Las decisiones se documentan en la memoria; los requisitos concretos, en una especificación aprobada; los pasos, en un plan aprobado; y el trabajo ejecutable, en tareas vinculadas a esos artefactos. La memoria no sustituye una especificación y una especificación no aprueba decisiones que sigan pendientes.

## 16. Optimización y No Redundancia

Reducir duplicación de reglas, lecturas, delegaciones, preguntas y revisiones solo cuando se mantengan las garantías de autoridad, seguridad, trazabilidad, desacoplamiento, verificación y rigor. Si una optimización pudiera reducir alguna garantía, no se aplica sin aprobación explícita del usuario.
