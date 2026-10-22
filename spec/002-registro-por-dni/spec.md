# SPEC — Registro de cuenta identificando el cliente por DNI

## 1. Problema

El formulario de registro (`login.html`) exige el ID interno de la tabla
`clientes` (`idCliente`, entero). Ese valor no es conocido por el usuario y
hace que el alta sea poco intuitiva; además, un error de tipeo produce mensajes
de validación confusos.

## 2. Objetivo

Permitir que el usuario se registre indicando el DNI de su perfil de cliente
dentro del negocio, y que el backend resuelva ese DNI al cliente existente para
crear el vínculo.

## 3. Requisitos y Reglas de Negocio

- El request de `POST /api/auth/registro` reemplaza `idCliente` (int) por
  `dniCliente` (String, 7 u 8 dígitos, solo números).
- El backend busca el cliente por DNI **dentro del negocio resuelto**; si no
  existe, responde `400` con mensaje claro y no crea la cuenta.
- El vínculo `cuentas_negocio` se guarda con el ID real del cliente resuelto.
- `POST /api/auth/vincular` (sesión autenticada) conserva `idCliente`; queda
  fuera del alcance de esta especificación.
- Sin cambios de esquema de base de datos.

## 4. Casos de Uso

- Caso 1: usuario ingresa DNI válido de un cliente del negocio → se crea la
  cuenta y el vínculo con `idCliente` resuelto (rol `CLIENTE`).
- Caso 2: DNI inexistente en el negocio → `400`, sin cuenta creada.
- Caso 3: DNI de un cliente de otro negocio → `400`, sin cuenta creada.
- Caso 4: DNI con formato inválido (letras, longitud incorrecta) → `400` de
  validación del DTO.

## 5. Criterios de Aceptación

- [x] El frontend muestra el campo "DNI" (texto, sin puntos) y envía
  `dniCliente`.
- [x] `CuentaRegistroDTO` valida DNI obligatorio, numérico y de 7-8 dígitos.
- [x] El DNI se resuelve contra `clientes` filtrando por `id_negocio` del
  request.
- [x] Si el DNI no existe, no se persiste ninguna cuenta nueva.
- [x] `mvn test` pasa completo.
- [x] `API_DOCUMENTATION.md` refleja el nuevo contrato.

## 6. Restricciones

- Respetar `docs/constitution.md` y `AGENTS.md`.
- Sin lambdas nuevas innecesarias; nombres en español en dominio/aplicación.
- No introducir tecnologías ni patrones nuevos.

## 7. Decisiones y Aprobaciones

| Decisión | Contexto y alternativas | Decisión explícita del usuario | Estado/fecha |
|----------|-------------------------|--------------------------------|--------------|
| Identificar al cliente por DNI en el registro | Alternativas: aceptar DNI o ID en un solo campo; DNI con verificación previa mostrando nombre (requiere endpoint público de consulta) | "DNI en el campo": el campo pasa a texto y el backend resuelve el DNI dentro del negocio | Aprobada por el usuario, 2026-10-18 |
