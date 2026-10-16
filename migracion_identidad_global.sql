-- Migración: identidad global multi-tenant (cuentas + cuentas_negocio).
-- Reemplaza a migracion_cuentas_auth.sql (preservado como migracion_cuentas_auth.legado.sql,
-- nunca aplicado a la base real): el modelo anterior ataba 1 cuenta a 1 cliente/negocio.
--
-- MySQL 8.0.45. NO ejecutar en producción sin autorización expresa.
-- Tablas comerciales existentes (clientes, ventas, pagos, ...) NO se modifican.
--
-- ============================================================================
-- FASE 0 — PREVALIDACIÓN (solo lectura; ejecutar antes de la FASE 1)
-- ============================================================================
-- 0.1 Las tablas objetivo no deben existir (greenfield):
-- SELECT TABLE_NAME FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME IN ('cuentas', 'cuentas_negocio');
--  -- Esperado: 0 filas. Si hay filas, detener y comparar estructura.
--
-- 0.2 Estructura real de clientes (columnas, nulos, claves):
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA
--  FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'clientes'
--  ORDER BY ORDINAL_POSITION;
--  -- Requerido: id INT PK AUTO_INCREMENT, id_negocio BIGINT NOT NULL.
--
-- 0.3 Únicos y constraints de clientes/negocio:
-- SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME IN ('clientes', 'negocio');
-- SELECT CONSTRAINT_NAME, COLUMN_NAME, ORDINAL_POSITION
--  FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'clientes'
--    AND CONSTRAINT_NAME IN ('uq_clientes_negocio_dni', 'uq_clientes_id_negocio', 'fk_clientes_negocio')
--  ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION;
--  -- Requerido: uq_clientes_id_negocio sobre (id, id_negocio) en ese orden
--  -- (la FK compuesta de cuentas_negocio la referencia).
--
-- 0.4 Clientes huérfanos (id_negocio sin negocio):
-- SELECT c.id, c.dni, c.id_negocio FROM clientes c
--  LEFT JOIN negocio n ON n.id = c.id_negocio WHERE n.id IS NULL;
--  -- Esperado: 0 filas.
--
-- 0.5 Duplicados que romperían UNIQUE(id_negocio, dni):
-- SELECT id_negocio, dni, COUNT(*) FROM clientes
--  GROUP BY id_negocio, dni HAVING COUNT(*) > 1;
--  -- Esperado: 0 filas.
--
-- 0.6 Negocios inactivos (no bloquean la migración, solo informan):
-- SELECT id, nombre, subdominio FROM negocio WHERE activo != 1 OR fecha_baja IS NOT NULL;
--
-- 0.7 Pares (id_cliente, id_negocio) de ventas sin perfil en clientes:
-- SELECT DISTINCT v.id_cliente, v.id_negocio FROM ventas v
--  LEFT JOIN clientes c ON c.id = v.id_cliente AND c.id_negocio = v.id_negocio
--  WHERE c.id IS NULL;
--  -- Esperado: 0 filas (no bloquea; solo diagnostica).
--
-- ============================================================================
-- FASE 1 — DDL (idempotente: CREATE TABLE IF NOT EXISTS)
-- Si alguna tabla YA existe con otra estructura, este script NO la altera:
-- detener y comparar con SHOW CREATE TABLE <tabla>.
-- ============================================================================

CREATE TABLE IF NOT EXISTS cuentas (
    id INT NOT NULL AUTO_INCREMENT,
    nombre_cuenta VARCHAR(100) NOT NULL,
    correo_electronico VARCHAR(150) NOT NULL,
    contrasenia VARCHAR(255) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    fecha_alta DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_baja DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cuentas_correo (correo_electronico),
    CONSTRAINT chk_cuentas_estado CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'BAJA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cuentas_negocio (
    id INT NOT NULL AUTO_INCREMENT,
    id_cuenta INT NOT NULL,
    id_negocio BIGINT NOT NULL,
    id_cliente INT NOT NULL,
    rol VARCHAR(50) NOT NULL DEFAULT 'CLIENTE',
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    fecha_alta DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_baja DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cn_cuenta_negocio (id_cuenta, id_negocio),
    KEY idx_cn_cliente_negocio (id_cliente, id_negocio),
    CONSTRAINT fk_cn_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuentas (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cn_negocio FOREIGN KEY (id_negocio) REFERENCES negocio (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_cn_cliente_negocio FOREIGN KEY (id_cliente, id_negocio)
        REFERENCES clientes (id, id_negocio)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_cn_rol CHECK (rol IN ('ADMIN', 'CLIENTE')),
    CONSTRAINT chk_cn_estado CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'BAJA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- FASE 2 — VERIFICACIÓN POSTERIOR (qué se creó y qué se omitió)
-- ============================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME IN ('cuentas', 'cuentas_negocio');
--  -- Esperado: 2 filas.
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
--  FROM information_schema.TABLE_CONSTRAINTS
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME IN ('cuentas', 'cuentas_negocio')
--  ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;
--  -- Esperado: PK + UNIQUE + 3 CHECK en cuentas; PK + UNIQUE + 3 FK + 2 CHECK en cuentas_negocio.
-- SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
--  FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA = 'ecommerce_db' AND TABLE_NAME = 'cuentas_negocio'
--    AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY CONSTRAINT_NAME, ORDINAL_POSITION;
--  -- Esperado: fk_cn_cuenta -> cuentas.id; fk_cn_negocio -> negocio.id;
--  -- fk_cn_cliente_negocio -> clientes.(id, id_negocio) en ese orden.
-- SELECT COUNT(*) AS cuentas, (SELECT COUNT(*) FROM cuentas_negocio) AS vinculos
--  FROM cuentas;
--  -- Esperado en greenfield: 0, 0 (no se crea ningún dato ficticio).
--
-- ============================================================================
-- REVERSIÓN (solo con tablas vacías; nunca en producción sin autorización)
-- ============================================================================
-- SELECT COUNT(*) FROM cuentas_negocio; -- debe ser 0
-- SELECT COUNT(*) FROM cuentas;         -- debe ser 0
-- DROP TABLE IF EXISTS cuentas_negocio;
-- DROP TABLE IF EXISTS cuentas;
