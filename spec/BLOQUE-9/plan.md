# Plan: Implementación de Paginación (BLOQUE-9)

## Objetivo
Exponer paginación en endpoints de listado y adaptar Frontend para soporte dual.

## Backend (Controladores)
1.  Identificar endpoints de listado (`Ventas`, `Productos`, `Clientes`, `Auditoría`).
2.  Modificar métodos de listado para aceptar `page` y `size` (Query Params).
3.  Implementar validación de parámetros (Defaults: `page=1`, `size=20`).
4.  Calcular `offset = (page - 1) * size`.
5.  Actualizar servicios para soportar `limit` y `offset`.
6.  Retornar `ResultadoPaginado<T>`.

## Frontend (JavaScript)
1.  Modificar lógica de `fetch` para manejar la respuesta dual:
    *   Si es `Array` -> Renderizar normalmente (retrocompatibilidad).
    *   Si es `ResultadoPaginado` -> Renderizar `datos` y actualizar paginación (botones).
2.  Crear componente de paginación (botones Anterior/Siguiente).
3.  Gestionar estado de botones según `paginaActual` y `totalPaginas`.

## Verificación
1.  `mvn test` (asegurar retrocompatibilidad).
2.  Prueba manual de paginación en UI.
