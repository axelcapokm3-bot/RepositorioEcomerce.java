-- OBSOLETO: este archivo NO es una migración y NO debe ejecutarse como DDL.
--
-- Las tablas `cuentas` y `cuentas_negocio` YA EXISTEN en `ecommerce_db` con la
-- estructura definitiva verificada el 2026-10-16 (information_schema +
-- SHOW CREATE TABLE). El código Java fue adaptado a esa estructura real; no se
-- requiere crear, recrear ni alterar nada.
--
-- Este archivo se conserva únicamente como verificación de compatibilidad de
-- SOLO LECTURA para otros entornos. Ejecutar únicamente los SELECT.
--
-- Estructura real esperada (resumen):
--   cuentas:         id BIGINT PK AI, nombre_cuenta VARCHAR(100) NOT NULL,
--                    correo_electronico VARCHAR(254) NOT NULL UNIQUE
--                    (uq_cuentas_correo), contrasenia VARCHAR(255) NOT NULL,
--                    activo TINYINT(1) NOT NULL DEFAULT 1, timestamps.
--   cuentas_negocio: PK compuesta (id_cuenta, id_negocio), sin id propio,
--                    id_cliente INT NOT NULL,
--                    UNIQUE (id_negocio, id_cliente),
--                    FK compuesta (id_cliente, id_negocio) -> clientes(id, id_negocio),
--                    FK id_cuenta -> cuentas(id), FK id_negocio -> negocio(id),
--                    rol_usuario VARCHAR(30) DEFAULT 'CLIENTE',
--                    activo TINYINT(1) NOT NULL DEFAULT 1.

-- 1. Las tablas deben existir (esperado: 2 filas).
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ecommerce_db'
   AND TABLE_NAME IN ('cuentas', 'cuentas_negocio');

-- 2. Columnas de `cuentas` (esperado: id bigint, correo_electronico varchar(254),
--    activo tinyint y sin columnas `estado`, `rol` ni `id_cliente`).
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'cuentas'
 ORDER BY ORDINAL_POSITION;

-- 3. Columnas y clave primaria de `cuentas_negocio` (esperado: PK compuesta
--    (id_cuenta, id_negocio), columna rol_usuario, columna activo, sin `id`).
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'cuentas_negocio'
 ORDER BY ORDINAL_POSITION;

-- 4. Claves foráneas (esperado: 4 filas; la compuesta referencia
--    clientes(id, id_negocio) en ese orden, posiciones 1 y 2).
SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME,
       REFERENCED_COLUMN_NAME, ORDINAL_POSITION
  FROM information_schema.KEY_COLUMN_USAGE
 WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'cuentas_negocio'
   AND REFERENCED_TABLE_NAME IS NOT NULL
 ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION;

-- 5. Índices y restricciones UNIQUE (esperado en cuentas_negocio:
--    PRIMARY (id_cuenta, id_negocio) y uq_cuentas_negocio_cliente
--    (id_negocio, id_cliente)).
SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'ecommerce_db'
   AND TABLE_NAME IN ('cuentas', 'cuentas_negocio')
 ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 6. Conteo informativo (sin datos ficticios; en greenfield 0 y 0).
SELECT (SELECT COUNT(*) FROM cuentas) AS cuentas,
       (SELECT COUNT(*) FROM cuentas_negocio) AS vinculos;
