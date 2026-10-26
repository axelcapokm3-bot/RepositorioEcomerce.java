-- AL-06: revocacion inmediata de tokens de acceso.
-- Se guarda solo la huella SHA-256 del token, nunca el token en claro.
-- Las filas expiran junto con el token; pueden purgarse periodicamente con:
--   DELETE FROM tokens_revocados WHERE expira_en <= NOW();

CREATE TABLE IF NOT EXISTS tokens_revocados (
    huella CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    id_cuenta BIGINT NOT NULL,
    motivo VARCHAR(50) NOT NULL DEFAULT 'LOGOUT',
    expira_en DATETIME NOT NULL,
    fecha_revocacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (huella),
    KEY idx_tokens_revocados_expira (expira_en),
    KEY idx_tokens_revocados_cuenta (id_cuenta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
