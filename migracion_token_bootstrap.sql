-- ME-02: token de bootstrap administrativo de un solo uso.
-- Solo se persiste la huella SHA-256; el valor en claro se configura en
-- AUTH_BOOTSTRAP_TOKEN y nunca se guarda en la base de datos.
-- La fila es global (id = 1): una vez usada no puede reutilizarse.
-- Para emitir un token nuevo, un operador debe borrar explícitamente la fila
-- y reiniciar el servidor con una nueva configuración.

CREATE TABLE IF NOT EXISTS tokens_bootstrap (
    id TINYINT NOT NULL DEFAULT 1,
    huella CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expira_en DATETIME NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    usado_en DATETIME NULL,
    usado_por VARCHAR(100) NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_tokens_bootstrap_unico CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
