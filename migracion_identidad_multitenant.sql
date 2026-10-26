-- AL-09: identidad global 1:N por negocio.
-- La cuenta (cuentas.id) es la identidad global; clientes es el perfil local y
-- cuentas_negocio la relacion 1:N. El constraint que impide multiples perfiles
-- por usuario es un UNIQUE global de una sola columna; el correcto es un UNIQUE
-- compuesto por negocio. En el esquema actual el equivalente de
-- UNIQUE(id_negocio, id_usuario_global) es UNIQUE(id_negocio, id_cuenta) en
-- cuentas_negocio (pk compuesta ya presente). Si un esquema externo conserva
-- clientes.id_usuario_global, este script tambien corrige ese UNIQUE.
-- Script idempotente: no falla si el esquema ya esta migrado.

DROP PROCEDURE IF EXISTS migrar_identidad_multitenant;

DELIMITER $$

CREATE PROCEDURE migrar_identidad_multitenant()
BEGIN
    DECLARE v_indice VARCHAR(64);

    -- Asegura un indice útil para la FK de cuentas_negocio antes de quitar
    -- cualquier UNIQUE legado de una sola columna sobre id_cliente. Un UNIQUE
    -- de una sola columna no sirve como reemplazo porque se elimina después.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS s
        WHERE s.TABLE_SCHEMA = DATABASE()
          AND s.TABLE_NAME = 'cuentas_negocio'
          AND s.COLUMN_NAME = 'id_cliente'
          AND s.SEQ_IN_INDEX = 1
          AND (
              s.NON_UNIQUE = 1
              OR EXISTS (
                  SELECT 1
                  FROM information_schema.STATISTICS s2
                  WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
                    AND s2.TABLE_NAME = s.TABLE_NAME
                    AND s2.INDEX_NAME = s.INDEX_NAME
                    AND s2.SEQ_IN_INDEX = 2
              )
          )
    ) THEN
        ALTER TABLE cuentas_negocio
            ADD INDEX idx_cuentas_negocio_cliente_fk (id_cliente, id_negocio);
    END IF;

    -- Elimina UNIQUE(dni) legado de clientes.
    SET v_indice = NULL;
    SELECT INDEX_NAME INTO v_indice
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'clientes'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'dni'
    LIMIT 1;

    WHILE v_indice IS NOT NULL DO
        SET @sql = CONCAT('ALTER TABLE clientes DROP INDEX `', v_indice, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET v_indice = NULL;
        SELECT INDEX_NAME INTO v_indice
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'clientes'
          AND NON_UNIQUE = 0
          AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'dni'
        LIMIT 1;
    END WHILE;

    -- Elimina UNIQUE(id_cliente) legado de cuentas_negocio.
    SET v_indice = NULL;
    SELECT INDEX_NAME INTO v_indice
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cuentas_negocio'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_cliente'
    LIMIT 1;

    WHILE v_indice IS NOT NULL DO
        SET @sql = CONCAT('ALTER TABLE cuentas_negocio DROP INDEX `', v_indice, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET v_indice = NULL;
        SELECT INDEX_NAME INTO v_indice
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cuentas_negocio'
          AND NON_UNIQUE = 0
          AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_cliente'
        LIMIT 1;
    END WHILE;

    -- Asegura un indice útil para la FK de id_cuenta antes de quitar un
    -- UNIQUE legado de una sola columna sobre id_cuenta.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS s
        WHERE s.TABLE_SCHEMA = DATABASE()
          AND s.TABLE_NAME = 'cuentas_negocio'
          AND s.COLUMN_NAME = 'id_cuenta'
          AND s.SEQ_IN_INDEX = 1
          AND (
              s.NON_UNIQUE = 1
              OR EXISTS (
                  SELECT 1
                  FROM information_schema.STATISTICS s2
                  WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
                    AND s2.TABLE_NAME = s.TABLE_NAME
                    AND s2.INDEX_NAME = s.INDEX_NAME
                    AND s2.SEQ_IN_INDEX = 2
              )
          )
    ) THEN
        ALTER TABLE cuentas_negocio
            ADD INDEX idx_cuentas_negocio_cuenta_fk (id_cuenta, id_negocio);
    END IF;

    -- Elimina UNIQUE(id_cuenta) legado de cuentas_negocio; impedia que una
    -- misma cuenta global tuviera vinculos en negocios distintos.
    SET v_indice = NULL;
    SELECT INDEX_NAME INTO v_indice
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cuentas_negocio'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_cuenta'
    LIMIT 1;

    WHILE v_indice IS NOT NULL DO
        SET @sql = CONCAT('ALTER TABLE cuentas_negocio DROP INDEX `', v_indice, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET v_indice = NULL;
        SELECT INDEX_NAME INTO v_indice
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cuentas_negocio'
          AND NON_UNIQUE = 0
          AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_cuenta'
        LIMIT 1;
    END WHILE;

    -- Crea el UNIQUE compuesto de clientes por negocio solo si no existe ya.
    IF NOT EXISTS (
        SELECT 1
        FROM (
            SELECT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'clientes'
              AND NON_UNIQUE = 0
            GROUP BY INDEX_NAME
            HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'id_negocio,dni'
        ) AS indices_clientes
    ) THEN
        ALTER TABLE clientes
            ADD UNIQUE INDEX uq_clientes_negocio_dni (id_negocio, dni);
    END IF;

    -- Crea el UNIQUE compuesto de cuentas_negocio por negocio solo si no existe ya.
    IF NOT EXISTS (
        SELECT 1
        FROM (
            SELECT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cuentas_negocio'
              AND NON_UNIQUE = 0
            GROUP BY INDEX_NAME
            HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'id_negocio,id_cliente'
        ) AS indices_cuentas_negocio
    ) THEN
        ALTER TABLE cuentas_negocio
            ADD UNIQUE INDEX uq_cuentas_negocio_cliente (id_negocio, id_cliente);
    END IF;

    -- Equivalente explicito de UNIQUE(id_negocio, id_usuario_global) para el
    -- esquema actual: un usuario global (cuentas.id) puede tener un solo perfil
    -- por negocio, pero varios perfiles en negocios distintos. La PK compuesta
    -- (id_cuenta, id_negocio) ya cubre el conjunto; solo se agrega si falta.
    IF NOT EXISTS (
        SELECT 1
        FROM (
            SELECT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cuentas_negocio'
              AND NON_UNIQUE = 0
            GROUP BY INDEX_NAME
            HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME) = 'id_cuenta,id_negocio'
        ) AS indices_cuentas_usuario
    ) THEN
        ALTER TABLE cuentas_negocio
            ADD UNIQUE INDEX uq_cuentas_negocio_negocio_usuario (id_negocio, id_cuenta);
    END IF;

    -- Compatibilidad con esquemas que aun guardan la identidad global en
    -- clientes.id_usuario_global. Primero retira cualquier UNIQUE global de una
    -- sola columna y luego crea UNIQUE(id_negocio, id_usuario_global).
    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'clientes'
          AND COLUMN_NAME = 'id_usuario_global'
    ) THEN
        SET v_indice = NULL;
        SELECT INDEX_NAME INTO v_indice
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'clientes'
          AND NON_UNIQUE = 0
          AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_usuario_global'
        LIMIT 1;

        WHILE v_indice IS NOT NULL DO
            SET @sql = CONCAT('ALTER TABLE clientes DROP INDEX `', v_indice, '`');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            SET v_indice = NULL;
            SELECT INDEX_NAME INTO v_indice
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'clientes'
              AND NON_UNIQUE = 0
              AND INDEX_NAME <> 'PRIMARY'
            GROUP BY INDEX_NAME
            HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id_usuario_global'
            LIMIT 1;
        END WHILE;

        IF NOT EXISTS (
            SELECT 1
            FROM (
                SELECT INDEX_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'clientes'
                  AND NON_UNIQUE = 0
                GROUP BY INDEX_NAME
                HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'id_negocio,id_usuario_global'
            ) AS indices_clientes_usuario
        ) THEN
            ALTER TABLE clientes
                ADD UNIQUE INDEX uq_clientes_negocio_usuario (id_negocio, id_usuario_global);
        END IF;
    END IF;
END$$

DELIMITER ;

CALL migrar_identidad_multitenant();
DROP PROCEDURE IF EXISTS migrar_identidad_multitenant;
