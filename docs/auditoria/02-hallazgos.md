# Hallazgos detallados

## Convenciones

- **Severidad**: `CRÍTICA` (compromiso directo o pérdida grave), `ALTA` (explotable o pérdida probable), `MEDIA` (degradación relevante con condiciones), `BAJA` (defensa en profundidad o higiene).
- **Confianza**: `CONFIRMADO` (verificado en código o ejecución), `PROBABLE` (código consistente con el fallo, falta prueba en entorno real), `NO VERIFICADO` (no se usa para el veredicto).
- Las rutas son relativas a la raíz del repositorio. Las líneas corresponden al árbol de trabajo auditado el 2026-10-21.

## Críticos

### CR-01 — Toma de control de un perfil de cliente ajeno por DNI o por ID

- **Severidad / confianza**: CRÍTICA / CONFIRMADO.
- **Evidencia**:
  - `AutenticacionService.java:172-201`: el registro busca un `Cliente` existente por DNI y lo vincula a la cuenta creada, sin verificar que el solicitante sea el titular.
  - `AutenticacionService.java:232-263`: `vincularNegocio` acepta cualquier `idCliente` del negocio; `validarPerfil` (`:328-332`) solo comprueba existencia.
  - `MySqlRepositorioCuentaNegocio.java:93-108`: `existeVinculo` no filtra `activo`, por lo que un vínculo dado de baja bloquea una re-vinculación legítima y no se reutiliza correctamente.
  - El esquema descrito en `docs/seguridad-operativa.md` impone `UNIQUE (id_negocio, id_cliente)`, que impide dos cuentas sobre el mismo perfil, pero no impide que la primera cuenta que llegue reclame un perfil libre.
- **Impacto**: un atacante puede registrarse con el DNI de una persona cuyo perfil ya existe en el negocio y obtener un token con el `idCliente` de la víctima. Con ese token puede consultar ventas, datos personales y operar como ese cliente. Si el perfil ya está vinculado a otra cuenta, el ataque se bloquea; si está libre, es explotable.
- **Reproducción conceptual**:
  1. Un operador crea un cliente con DNI `12345678` y no lo vincula a una cuenta.
  2. El atacante hace `POST /api/auth/registro` con `dniCliente=12345678`, su propio email y contraseña.
  3. El servicio reutiliza el `Cliente` existente y emite una sesión para ese `idCliente`.
  4. El atacante consulta `GET /api/ventas/{id}` o los datos del cliente según su rol.
- **Remediación**:
  - No vincular automáticamente perfiles existentes por DNI. Exigir invitación de un administrador, código de un solo uso, verificación por email/SMS o re-autenticación con un canal ya verificado.
  - Separar “crear perfil” de “reclamar perfil”; el segundo debe ser una operación auditada y con prueba de titularidad.
  - Hacer `vincularNegocio` una operación administrativa o con aprobación del titular; nunca aceptar un `idCliente` arbitrario de un usuario autenticado.
  - Revisar vínculos inactivos y permitir reactivación controlada.
- **Prueba de regresión**: intento de registro con DNI de un cliente existente no vinculado debe fallar con `403/409` sin emitir token; intento de `vincular` con `idCliente` ajeno debe fallar; el titular legítimo sí debe poder completar el flujo.

### CR-02 — Registro admin con email existente e impersonación cross-tenant

- **Severidad / confianza**: CRÍTICA / CONFIRMADO.
- **Evidencia**:
  - `AutenticacionService.java:159-171`: si el email ya existe, se rechaza salvo que `rol == ADMIN`, el solicitante sea admin y pertenezca al negocio (`esAltaAdmin`).
  - `AutenticacionService.java:177-201`: en ese caso reutiliza la `Cuenta` existente y emite una sesión sin validar la contraseña del titular ni pedir consentimiento.
  - `AutenticacionService.java:379-384`: el token se emite con el `idCuenta` global de la víctima.
  - `AutenticacionService.java:266-280` + `ControlAcceso.java:20-25`: `cambiarNegocio` busca el vínculo por `idCuenta`; el token permite operar en otros negocios donde la víctima tenga vínculo.
- **Impacto**: un administrador malicioso o comprometido del negocio A puede registrar el email de una víctima como ADMIN, recibir una sesión de la cuenta global de la víctima y luego cambiar al negocio B si la víctima también pertenece allí. Es una toma de cuenta cross-tenant, no solo un duplicado de email.
- **Condiciones**: la cuenta víctima debe estar activa, no debe estar ya vinculada al negocio A y el atacante debe conocer su email. No requiere la contraseña de la víctima.
- **Remediación**:
  - Nunca emitir una sesión para una cuenta existente sin autenticarla. Si un admin quiere invitar a un email existente, generar una invitación con token de un solo uso y confirmación del titular.
  - Registrar la intención y la aprobación en auditoría; notificar al titular.
  - No permitir que un admin de un tenant adjunte una cuenta global existente a su tenant sin consentimiento.
  - Añadir pruebas de intento de secuestro por email y de cambio de negocio posterior.
- **Prueba de regresión**: admin A intenta registrar el email de la víctima; la API debe responder `409/403` o crear una invitación pendiente, nunca un token de sesión. La víctima no debe aparecer vinculada al tenant A.

## Altos

### AL-01 — Replay de checkout para ventas canceladas o ya pagadas

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `PagoService.java:47-71`: si existe un pago con operación externa, devuelve `crearResultado(pago)` antes de llamar a `validarVentaPendiente`.
  - `PagoService.java:281-286`: “operación externa” se considera por `orderId`/`mpPaymentId`, sin mirar el estado, la expiración ni la venta.
  - `VentaService.java:387-400`: `POST /api/ventas/{id}/pago` carga la venta y llama directamente a `iniciarCheckout`.
  - `PagoService.java:150-153`: si el pago aprobado llega tarde a una venta cancelada, solo se registra un warning para conciliación.
- **Impacto**: un usuario puede cancelar una venta, volver a solicitar el pago y recibir la URL antigua; si paga, el dinero se captura sin una venta válida o con stock ya reintegrado. También puede recuperar un checkout de una venta ya pagada y generar un segundo cobro.
- **Remediación**:
  - Validar el estado de la venta **antes** de devolver cualquier checkout existente.
  - Si la venta está cancelada, pagada, enviada o expirada, responder `409` y no reutilizar la sesión.
  - Expirar/cerrar la sesión de la pasarela al cancelar; conciliar y reembolsar pagos tardíos.
  - Añadir una prueba de replay después de `cancelar` y después de `PAGADO`.
- **Prueba de regresión**: cancelar venta con checkout; `POST /pago` debe fallar; un webhook aprobado posterior no debe dejar un pago aprobado sin decisión de reembolso.

### AL-02 — Degradación e inconsistencia de estados de pago

- **Severidad / confianza**: ALTA / CONFIRMADO (código); PROBABLE (evento real fuera de orden).
- **Evidencia**:
  - `PagoService.java:145-165`: el estado del pago se compara contra el objeto `pago` recibido, sin `SELECT ... FOR UPDATE` de la fila `pagos`.
  - `MySqlRepositorioPago.java:237-251`: `actualizarWebhook` actualiza estado e identificadores sin condición de estado ni transición permitida.
  - `PagoService.java:116-127`: la ruta sin transacción también escribe el nuevo estado directamente.
  - `EstadoVenta.java`: la venta sí protege transiciones, pero `EstadoPago` no tiene una máquina equivalente.
- **Impacto**: un evento duplicado, tardío o fuera de orden puede dejar `pago = RECHAZADO/PENDIENTE` mientras `venta = PAGADO`, o viceversa. El pago aprobado puede perder su estado terminal y la conciliación se vuelve ambigua.
- **Remediación**:
  - Implementar transiciones explícitas para `EstadoPago` (`PENDIENTE → APROBADO/RECHAZADO/CANCELADO`; terminales no vuelven atrás).
  - Bloquear la fila del pago con `FOR UPDATE` dentro de la misma transacción que la venta.
  - Ordenar por fecha/evento de la pasarela y descartar eventos obsoletos; guardar el último evento aplicado.
  - Pruebas de eventos duplicados, invertidos y concurrentes.
- **Prueba de regresión**: aprobado seguido de pendiente/rechazado no debe degradar; dos webhooks concurrentes no deben dejar estados divergentes.

### AL-03 — Webhooks confirmados antes de un procesamiento durable

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `WebhookController.java:81-82`: responde `200` y luego llama a `webhookService.procesarAsync`.
  - `StripeWebhookController.java:38-40`: mismo patrón para Stripe.
  - `WebhookService.java:257-262` y `StripeWebhookService.java:128-130`: los errores de procesamiento se registran en log, no se propagan al proveedor.
  - `RegistroExecutors`/`ServiceExecutor`: la cola es en memoria; un reinicio o saturación pierde tareas.
- **Impacto**: el proveedor considera entregado el evento y no reintenta. Si la base está caída, la cola se llena o el proceso se reinicia, la venta queda pendiente aunque el cliente haya pagado.
- **Remediación**:
  - Persistir el evento crudo en una tabla inbox/idempotencia antes de responder `200`; procesar desde ahí con reintentos y dead-letter.
  - Responder `5xx` ante fallos retryables y `2xx` solo tras persistir la recepción.
  - Hacer el handler idempotente por ID de evento/pago.
  - Monitorear cola, antigüedad del evento y pendientes.
- **Prueba de regresión**: matar el proceso después del ACK no debe perder el evento; saturar la cola debe producir `503`, no `200` silencioso.

### AL-04 — Fan-out de Mercado Pago y primera notificación multi-tenant incompletos

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `WebhookService.java:347-369`: `procesarPagoPersistido` devuelve `true` si falta access token o si la API falla, impidiendo que el caller continúe.
  - `WebhookService.java:315-345`: `procesarPaymentMultiNegocio` hace `return` si un token no encuentra un pago local, en vez de continuar con los demás negocios.
  - `WebhookService.java:297-313`: la primera notificación de una `Order` sin pago persistido exige un token único; con más de un negocio activo no se procesa (“modo multi-cuenta queda pendiente”).
- **Impacto**: en una instalación con varios negocios, una notificación puede quedar sin procesar o resolverse contra el tenant equivocado. El pago queda pendiente y el proveedor no reintenta porque ya recibió `200`.
- **Remediación**:
  - Iterar todos los tokens/negocios y no retornar hasta agotar candidatos; usar una tabla de mapeo `order_id/payment_id → negocio`.
  - Resolver primero por pago persistido; si no existe, usar una referencia externa firmada o un índice global.
  - Devolver fallo retryable cuando no se pueda asociar, en lugar de aceptar silenciosamente.
  - Pruebas con dos negocios y una notificación de Order inicial.
- **Prueba de regresión**: dos tenants con tokens distintos; el evento del tenant B debe procesarse aunque el tenant A no tenga el pago.

### AL-05 — Productos inactivos listados y comprables

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `MySqlRepositorioProducto.java:81-118`: `buscarPorId`, `buscarPorIdParaUpdate` y `listarTodos` filtran por `id_negocio`, pero no por `activo`.
  - `VentaController.java:201-218`: `construirCarrito` agrega el producto sin comprobar `isActivo()`.
  - `VentaService.java:329-369`: `validarYActualizarStock` relee el producto y descuenta stock sin comprobar `isActivo()`.
  - `ProductoService.java:56-68`: el listado público devuelve todos los productos del negocio.
- **Impacto**: un producto dado de baja sigue visible en el catálogo y puede comprarse por API directa, generando ventas de artículos que el negocio quiso retirar. También puede agotar stock reservado.
- **Remediación**:
  - Filtrar `activo = 1` en listados públicos y en la compra; crear un método administrativo separado para ver inactivos.
  - Añadir una regla de dominio `producto.puedeVenderse()` y aplicarla en carrito, compra directa y checkout.
  - Prueba de producto inactivo en catálogo, carrito y `POST /api/ventas/crear`.
- **Prueba de regresión**: producto con `activo=0` no aparece en catálogo y cualquier intento de compra responde `404/409`.

### AL-06 — Token sin revocación ni verificación de estado

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `ProveedorTokenAcceso.java:52-89`: valida firma, formato y expiración; no consulta cuenta, vínculo, rol ni negocio.
  - `BaseController.java:207-234`: `exigirAutenticacion` solo valida el token.
  - `ControlAcceso.java`: compara IDs/rol del token; no consulta estado.
  - `AutenticacionService.java` solo valida cuenta/vínculo activo al ingresar o cambiar de negocio, no en cada request.
- **Impacto**: tras bloquear una cuenta, revocar un vínculo, degradar un rol o dar de baja un negocio, el token anterior sigue operando hasta 12 horas (o hasta 72 si se configura el máximo). Un token robado no se puede revocar.
- **Remediación**:
  - Añadir versión de sesión/`token_version` o lista de revocación; verificar cuenta/vínculo/negocio activo en cada request o en un filtro cacheado con TTL corto.
  - Reducir TTL y emitir refresh tokens si se necesita comodidad.
  - Invalidar tokens al cambiar rol, contraseña o estado del negocio.
- **Prueba de regresión**: token válido deja de funcionar inmediatamente después de desactivar la cuenta o el vínculo.

### AL-07 — Caché de tenant obsoleto tras desactivar un negocio

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `ResolutorNegocio.java:16-23`: `CACHE` estático; solo se limpia en `inicializar`.
  - `ResolutorNegocio.java:35-48`: en un cache hit devuelve el ID sin volver a consultar; en un cache miss sí verifica `activo` y `fechaBaja` (`:44`).
  - `AutenticacionService.java:147-201` (`registrar`) no llama a `validarNegocioActivo`; `vincularNegocio` y `cambiarNegocio` sí lo hacen (`:255`, `:278`). La ruta HTTP pasa por el resolutor, pero un caché obsoleto o una llamada directa al servicio omiten la comprobación.
  - `NegocioService` no invalida el caché al actualizar o dar de baja un negocio.
- **Impacto**: un negocio activo que se cachea y luego se desactiva sigue resolviendo hasta reiniciar el servidor. Endpoints públicos y autenticados pueden seguir operando contra ese tenant.
- **Remediación**:
  - Añadir TTL corto y invalidación explícita al cambiar estado/configuración del negocio.
  - Alternativa: cachear solo IDs y validar `activo` en cada request con una consulta barata o caché versionada.
- **Prueba de regresión**: resolver negocio, desactivarlo, volver a resolver; debe fallar sin reiniciar.

### AL-08 — Stock retenido antes del checkout sin expiración ni compensación

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `VentaService.java:159-183`: `persistirVentaAtomica` descuenta stock y guarda la venta.
  - `VentaService.java:256-273`: la creación de checkout idempotente hace lo mismo antes de llamar a la pasarela.
  - `PagoService.java:77-88`: la pasarela se invoca después de persistir venta/pago.
  - No hay job de expiración ni liberación automática; la cancelación es manual (`VentaService.cancelarVentaAsync`).
- **Impacto**: un checkout abandonado o una pasarela caída dejan stock retenido indefinidamente. Un atacante puede agotar inventario creando ventas pendientes sin pagar.
- **Remediación**:
  - Reserva con TTL y job de expiración; liberar stock al vencer o al fallar la creación del checkout.
  - Saga/compensación: si la pasarela falla, cancelar la venta y reintegrar stock en la misma operación o con reintento.
  - Separar “stock físico”, “reservado” y “disponible” con transiciones auditadas.
- **Prueba de regresión**: crear venta, simular fallo de pasarela, verificar stock liberado; vencer el TTL y verificar liberación.

### AL-09 — Idempotencia global de ventas entre negocios

- **Severidad / confianza**: ALTA / CONFIRMADO.
- **Evidencia**:
  - `migracion_idempotencia_ventas_pagos.sql:11-13`: `UNIQUE KEY uq_ventas_clave_idempotencia (clave_idempotencia)` es global, no por negocio.
  - `VentaService.java:230-235`: la búsqueda existente se hace por `(idNegocio, clave)`.
  - `MySqlRepositorioVenta` persiste la clave y la base impone la unicidad global.
- **Impacto**: dos negocios que usen la misma clave (por ejemplo, un cliente que reutiliza una clave entre tenants o una colisión deliberada) hacen que el segundo insert falle aunque su búsqueda local no encuentre nada. Puede provocar errores `500`, denegación de compra o confusión de idempotencia.
- **Remediación**:
  - Migrar a `UNIQUE (id_negocio, clave_idempotencia)` y ajustar la consulta/constraint.
  - Validar duplicados existentes antes de migrar; plan de rollback.
  - Prueba de dos tenants con la misma clave.
- **Prueba de regresión**: crear venta en tenant A con clave `k`; crear venta en tenant B con la misma clave debe funcionar de forma independiente.

## Medios

### ME-01 — Registro no transaccional y enumeración de emails

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**: `AutenticacionService.java:177-201` guarda cuenta, cliente y vínculo mediante repositorios que abren conexiones separadas; no hay `ITransaccion`. `:164-166` responde “El correo electrónico ya está registrado”.
- **Impacto**: un fallo intermedio deja cuentas/clientes huérfanos; la respuesta 409 permite enumerar emails registrados.
- **Remediación**: una transacción única para cuenta+cliente+vínculo; respuesta genérica y rate limit por IP/email; auditoría de intentos.
- **Prueba**: simular fallo al guardar el vínculo y verificar rollback; comparar respuestas para email existente/no existente.

### ME-02 — Token de bootstrap reutilizable

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**: `AutenticacionService.java:353-367` acepta `tokenSetup` para cualquier rol elevado; `:370-376` solo compara el token, sin marca de uso, expiración ni auditoría.
- **Impacto**: si el token de bootstrap se filtra o se olvida en el entorno, permite crear administradores repetidamente. La documentación operativa pide eliminarlo, pero el código no lo impone.
- **Remediación**: token de un solo uso persistido, expiración corta, IP allowlist, auditoría y eliminación automática tras el primer admin.
- **Prueba**: segundo uso del token debe fallar.

### ME-03 — Construcción manual del JSON de error

- **Severidad / confianza**: MEDIA / CONFIRMADO (código); PROBABLE (explotación).
- **Evidencia**: `BaseController.java:98-101` arma `{"error":"..."}` y solo reemplaza `"`; no escapa `\` ni caracteres de control. `JsonUtils.java:88-116` sí tiene un escape correcto, pero no se usa aquí.
- **Impacto**: un mensaje con backslash, salto de línea o control puede producir JSON inválido o alterar el campo `error`. La explotación depende de que el mensaje incluya datos controlados por el atacante (por ejemplo, nombres en errores de stock).
- **Remediación**: usar `JsonUtils.toJson(Map.of("error", message))` o `ObjectMapper`; nunca concatenar.
- **Prueba**: mensaje con `\`, `"`, `\n` y `\u0000` debe seguir siendo JSON válido.

### ME-04 — Rate limiting local, evitable y no consciente de proxy

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**: `LimitadorTasa.java:28-49` usa un `ConcurrentHashMap` por JVM; `:111-130` expulsa la ventana más antigua, incluso activa, cuando se supera el máximo; `BaseController.java:350-354` usa solo `exchange.getRemoteAddress()`, sin `X-Forwarded-For`.
- **Impacto**: detrás de un proxy todos los clientes comparten IP; con varias instancias el límite se multiplica; un atacante puede inundar claves únicas para expulsar ventanas activas y evadir el límite. La limpieza O(n) agrava la carga.
- **Remediación**: rate limit distribuido (Redis) o al menos consciente de proxy confiable; no expulsar ventanas activas; usar estructuras acotadas y métricas.
- **Prueba**: 10k claves únicas no deben eliminar la ventana de login activa; con `X-Forwarded-For` confiable debe limitar por IP real.

### ME-05 — Precedencia de configuración inconsistente

- **Severidad / confianza**: MEDIA / CONFIRMADO (código); PROBABLE (impacto en despliegue).
- **Evidencia**:
  - `ConfigLoader.java:38-49`: system property → archivo → env.
  - `DatabaseConnection.java:77-88`: archivo → system property → env.
  - `ConfiguracionMercadoPago.java:51-65`: system → env → archivo → DB.
  - `docs/seguridad-operativa.md` declara system → archivo → env.
- **Impacto**: una variable de entorno o una propiedad del sistema puede ser ignorada en un componente y prevalecer en otro; el servidor puede conectarse a una base o usar credenciales distintas de las esperadas.
- **Remediación**: una única fuente de resolución con precedencia documentada y probada; tests de las cuatro combinaciones para DB, MP, Stripe y Redis.
- **Prueba**: definir valores distintos en archivo/env/system y verificar el valor efectivo en cada componente.
- **Estado**: implementado el 2026-10-26 con `ResolutorConfiguracion` (system → env → archivo) para `ConfigLoader`, `DatabaseConnection`, `ConfiguracionMercadoPago`, auth, perfiles y webhooks; fail-fast en producción y 8 tests de resolución. Pendiente revalidación final de Bloque 7.

### ME-06 — Validación débil de URLs de retorno y monto en Stripe

- **Severidad / confianza**: MEDIA / PROBABLE.
- **Evidencia**: `PasarelaStripe.java:35-49` solo exige que `stripe.success-url` contenga `{CHECKOUT_SESSION_ID}`; no exige HTTPS, host ni allowlist. `:71-108` crea la sesión con esa URL. `validar` (`:52-68`) valida precisión por ítem, pero no un máximo total.
- **Impacto**: una configuración maliciosa o errónea puede enviar al usuario a un sitio HTTP/ajeno después del pago (phishing/open redirect). Un total fuera del máximo de Stripe puede fallar en la pasarela con un error poco claro.
- **Remediación**: validar `URI` absoluta, HTTPS, host permitido y ausencia de userinfo/fragmento, como ya hace `PasarelaMercadoPago`; validar monto total y moneda.
- **Prueba**: URL `http://evil.example`, `https://evil.example`, `javascript:` y `https://good.example` deben aceptarse/rechazarse según política.

### ME-07 — Firma de Mercado Pago sin frescura ni anti-replay

- **Severidad / confianza**: MEDIA / PROBABLE.
- **Evidencia**: `WebhookService.java:149-185` parsea `ts` y lo incluye en el manifiesto, pero no compara `ts` con la hora actual ni guarda IDs de notificación procesados.
- **Impacto**: una notificación firmada capturada puede reenviarse dentro de la vida del secreto; la idempotencia de pagos reduce el impacto, pero el procesamiento se repite y consume recursos.
- **Remediación**: tolerancia de tiempo (por ejemplo, 5 minutos), rechazo de timestamps viejos/futuros, caché de `request-id`/evento y pruebas de replay.
- **Prueba**: firma válida con `ts` de hace 1 hora debe rechazarse; replay inmediato debe ser idempotente.

### ME-08 — Borrado de detalles antes del UPDATE de la venta

- **Severidad / confianza**: MEDIA / PROBABLE.
- **Evidencia**: `MySqlRepositorioVenta.java:161-191`: en modo `update`, el `DELETE` de `detalle_venta` (`:173-179`) se ejecuta antes del `UPDATE` de `ventas` (`:180`). La ruta pública `actualizar` usa autocommit (`:154-159`).
- **Impacto**: si el UPDATE falla después del DELETE, la venta queda sin detalles. En la ruta transaccional hay rollback, pero la ruta sin transacción es vulnerable.
- **Remediación**: ejecutar el UPDATE primero y borrar/insertar detalles después, o exigir transacción para toda actualización; prueba de fallo forzado.
- **Prueba**: forzar violación de constraint en el UPDATE y verificar que los detalles siguen existiendo.

### ME-09 — Excepciones genéricas y borrados silenciosos en servicios CRUD

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**: `ClienteService.java:71-93` y `ProductoService.java:71-95` lanzan `RuntimeException("... no encontrado")` en actualizar y no verifican existencia en eliminar. `BaseController.java:188-193` mapea excepciones desconocidas a `500`.
- **Impacto**: un recurso inexistente en actualización puede devolver `500` en vez de `404`; un borrado inexistente responde éxito, ocultando errores y complicando la idempotencia.
- **Remediación**: usar `RecursoNoEncontradoException`/`ControllerException.NoEncontrado`; verificar filas afectadas; respuesta `204/404` coherente.
- **Prueba**: `PUT/DELETE` sobre ID inexistente debe dar `404`, no `500/200`.

### ME-10 — Auditoría manipulable y consulta por producto incorrecta

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**:
  - `AuditoriaController.java:41-63`: cualquier ADMIN puede crear una entrada arbitraria con `accion`, `idEntidad` y `descripcion`.
  - `AuditoriaService.java:100-105`: `obtenerPorProductoAsync` consulta `buscarPorEntidad(idNegocio, "Venta", idProducto)`, usando el ID de producto como ID de venta.
  - `MySqlRepositorioAuditoria.java`: la tabla no tiene `id_negocio`; el filtro depende de `EXISTS` contra ventas/productos/clientes/pagos, por lo que entidades borradas ocultan sus registros.
- **Impacto**: un admin puede contaminar la traza; el endpoint de producto devuelve datos de ventas equivocadas; la auditoría no es confiable para incidentes.
- **Remediación**: hacer la auditoría append-only generada por el sistema, no por endpoint público; corregir entidad a `Producto`; añadir `id_negocio` o una tabla de alcance; conservar registros de entidades eliminadas.
- **Prueba**: consultar auditoría de producto y comparar con eventos reales; intento de POST manual debe rechazarse.

### ME-11 — Escaneo de secretos Stripe de todos los negocios

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**: `StripeWebhookService.java:81-98` prueba cada secreto hasta que uno valide; `:186-207` recorre todos los negocios activos y sus configuraciones en cada request.
- **Impacto**: cualquier secreto de tenant puede validar un evento; el procesamiento posterior valida sesión/monto, pero el costo por request crece con el número de tenants y se acepta un evento de un tenant con el secreto de otro.
- **Remediación**: endpoint/secreto por tenant, resolución por `account`/metadata antes de validar, caché de secretos con invalidación y rechazo de ambigüedad.
- **Prueba**: evento firmado con secreto del tenant B enviado a endpoint del tenant A debe rechazarse.

### ME-12 — Pool HTTP sin timeouts de request

- **Severidad / confianza**: MEDIA / CONFIRMADO (código); PROBABLE (explotación).
- **Evidencia**: `ServerMain.java` crea `ThreadPoolExecutor(50, 200, 60s, cola 2000, CallerRunsPolicy)`; no configura timeouts de lectura/escritura ni límites de cabeceras. `BaseController.leerCuerpoLimitado` limita el cuerpo a 1 MB, pero no el tiempo.
- **Impacto**: clientes lentos o conexiones colgadas pueden consumir hilos y cola; bajo carga, `CallerRunsPolicy` ejecuta trabajo en el acceptor y degrada la aceptación de conexiones.
- **Remediación**: reverse proxy con timeouts, límites de cabeceras y conexiones; ajustar pool/cola; monitorear saturación. Si se mantiene `HttpServer`, configurar propiedades de tiempo de `sun.net.httpserver` y probar carga.
- **Prueba**: prueba de conexiones lentas y de saturación de cola; el servidor debe rechazar/cerrar sin bloquear indefinidamente.

### ME-13 — Cobertura de pruebas insuficiente y tests vacíos

- **Severidad / confianza**: MEDIA / CONFIRMADO.
- **Evidencia**:
  - `test/java/com/CapaAplicacion/Service/StripeWebhookServiceTest.java` y `test/java/com/CapaInfraestructura/Implementacion/PasarelaStripeTest.java` tienen 0 bytes y están trackeados.
  - La suite pasa (216 tests), pero no contiene pruebas de las tomas de identidad `CR-01/CR-02`, productos inactivos, replay de checkout, durabilidad de webhooks, concurrencia de stock ni Stripe.
  - No hay tests E2E de navegador ni de contrato HTTP completo.
- **Impacto**: la suite da una señal de calidad engañosa; los fallos más graves no se detectarían en CI.
- **Remediación**: implementar la matriz de pruebas de `04-pruebas.md`; convertir los archivos vacíos en tests reales o eliminarlos; añadir cobertura de integración y E2E en sandbox.
- **Prueba**: los tests de regresión de los hallazgos deben fallar antes del fix y pasar después.

## Bajos

### BA-01 — Rollback que puede ocultar la excepción original

- **Severidad / confianza**: BAJA / CONFIRMADO.
- **Evidencia**: `JDBCTransaccion.java:12-30`: `conexion.rollback()` está dentro del `catch (Exception e)`; si el rollback lanza, se pierde la causa original.
- **Impacto**: diagnóstico difícil en fallos de base; no cambia el resultado de negocio.
- **Remediación**: capturar el fallo de rollback y añadirlo como `suppressed` a la excepción original.
- **Prueba**: simular rollback fallido y verificar que la excepción original se conserva.

### BA-02 — Inicialización estática de HikariCP

- **Severidad / confianza**: BAJA / CONFIRMADO.
- **Evidencia**: `DatabaseConnection.java:15` inicializa `DATA_SOURCE` en un `static final`; `:39-42` lanza `ExceptionInInitializerError` si falla.
- **Impacto**: el servidor falla al arrancar sin posibilidad de reintento; el mensaje puede no ser accionable. Es aceptable como fail-fast, pero debe documentarse y monitorearse.
- **Remediación**: mantener fail-fast con logging estructurado y health check; considerar inicialización perezosa con reintento si se requiere operación resiliente.
- **Prueba**: arranque sin DB debe fallar con un mensaje claro y código de salida conocido.

### BA-03 — Parámetros de PBKDF2 y límite de iteraciones

- **Severidad / confianza**: BAJA / CONFIRMADO.
- **Evidencia**: `AdaptadorContrasena.java:16-18` usa PBKDF2-HMAC-SHA256, 210.000 iteraciones, sal de 16 bytes y hash de 256 bits; `:56-74` acepta cualquier iteración positiva almacenada, sin tope.
- **Impacto**: 210k es aceptable pero conservador frente a Argon2id; un hash manipulado en DB podría provocar CPU excesiva en login. Requiere acceso a DB, por lo que el riesgo es bajo.
- **Remediación**: evaluar Argon2id o PBKDF2 ≥600k; limitar iteraciones aceptadas; migración transparente al iniciar sesión.
- **Prueba**: login con hash de iteraciones absurdas debe rechazarse o limitarse.

### BA-04 — XSS almacenado residual en el resumen de auditoría del dashboard

- **Severidad / confianza**: BAJA / CONFIRMADO (código); PROBABLE (explotación).
- **Evidencia**: `src/js/app.js:339` interpola `log.descripcion` sin `escaparHtml`; la página de auditoría completa sí escapa (`src/js/app.js:985-988`). `AuditoriaController.java:41-63` permite a un admin crear entradas.
- **Impacto**: un admin puede almacenar una descripción maliciosa que se ejecuta en el dashboard de otro admin; el token está en `sessionStorage`, por lo que un XSS podría robarlo. El alcance es admin-only y la ruta es limitada.
- **Remediación**: escapar `log.descripcion` con `escaparHtml` o usar `textContent`; añadir CSP; sanitizar la entrada de auditoría.
- **Prueba**: crear entrada con `<img src=x onerror=...>` y verificar que se muestra como texto.

### BA-05 — Actualizaciones/borrados que no verifican filas afectadas

- **Severidad / confianza**: BAJA / CONFIRMADO.
- **Evidencia**:
  - `MySqlRepositorioCliente.actualizar` y `eliminar` ejecutan `executeUpdate()` sin comprobar el resultado.
  - `MySqlRepositorioProducto.eliminar` hace lo mismo.
  - En contraste, `MySqlRepositorioProducto.actualizar` y `MySqlRepositorioPago` sí verifican filas.
- **Impacto**: una operación sobre un ID inexistente responde éxito y oculta inconsistencias; no corrompe datos por sí sola.
- **Remediación**: verificar `filas == 1` y lanzar `RecursoNoEncontradoException`/conflicto; unificar el patrón de repositorios.
- **Prueba**: actualizar/eliminar ID inexistente debe fallar con `404`.

### BA-06 — Higiene documental, artefactos y secreto local

- **Severidad / confianza**: BAJA / CONFIRMADO.
- **Evidencia**:
  - `API_DOCUMENTATION.md` declara CORS por defecto `localhost:5500,localhost:8080`; `BaseController.java:29-30` incluye además `127.0.0.1`.
  - `docs/seguridad-operativa.md` afirma que `migracion_identidad_global.sql` quedó obsoleto y no debe usarse; el archivo sigue en el repo.
  - `DocumentacionBaseDeDatos.txt` parece describir un esquema legacy distinto al real.
  - Hay archivos vacíos trackeados: `src/com/CapaAplicacion/Service/a`, `src/com/CapaDominio/s`, `src/com/CapaInfraestructura/a` y copias en `bin/`; `app.jfr` está presente.
  - `src/main/resources/config.properties` existe localmente con valores con apariencia de secretos; `pom.xml:66-72` lo excluye del artefacto y el commit `3789908` solo contiene `db.*`, pero el archivo debe permanecer fuera de Git y rotarse si fue real.
- **Impacto**: confusión operativa, riesgo de usar migraciones/contratos obsoletos y de exponer secretos si cambia el `.gitignore`.
- **Remediación**: limpiar archivos vacíos/artefactos, actualizar la documentación, marcar migraciones obsoletas con un README, ejecutar secret scanning y confirmar `.gitignore`.
- **Prueba**: `git ls-files` no debe incluir config local ni archivos basura; los enlaces/documentos deben coincidir con el código.

## Descartados o corregidos tras verificación

Estos puntos se investigaron y **no** se sostienen como hallazgos en el estado auditado:

- **No hay SQL injection directa** en los repositorios revisados: usan `PreparedStatement` y parámetros.
- **No hay XSS almacenado generalizado en catálogo/clientes**: `src/js/app.js` usa `escaparHtml` para nombres, DNI, localidad y descripciones de la página de auditoría; el residual es el dashboard (`BA-04`).
- **La ruta legacy `VentaService.crearVentaAsync` no está expuesta**: `VentaController.java:115-126` exige `Idempotency-Key` y usa `crearVentaConPagoAsync`; no se encontró un endpoint que llame a la variante insegura.
- **`ResolutorNegocio` sí verifica negocio activo en cache miss** (`:44`); el hallazgo `AL-07` es por entradas cacheadas que quedan obsoletas, no por aceptar inactivos en la primera consulta.
- **`MySqlRepositorioProducto.actualizar` sí verifica filas afectadas**; el problema de checks incompletos se limita a `eliminar` y a clientes (`BA-05`).
- **No se encontraron secretos de Mercado Pago/Stripe en el commit raíz**: solo `db.url`, `db.username`, `db.password` y `db.maximumPoolSize`; el riesgo es el archivo local no versionado y la rotación de credenciales DB históricas.
- **`JsonUtils` escapa correctamente**; el defecto es que `BaseController.sendError` no lo usa (`ME-03`).
- **No se confirmó una explotación de `sendError` con los datos actuales**: el impacto es corrupción de JSON si el mensaje contiene backslash/control; se mantiene como medio por la superficie y la falta de defensa.
