-- MySQL MCP read-only account for OpenCode.
-- Review this script before running it as a MySQL administrator.
-- It creates a dedicated account and grants SELECT only on ecommerce_db.
-- It does not modify ecommerce data, ecommerce tables, or their structure.

CREATE USER IF NOT EXISTS 'mcp_ecommerce'@'localhost'
  IDENTIFIED BY RANDOM PASSWORD
  WITH MAX_USER_CONNECTIONS 5;

-- Ensure this dedicated account has no privileges beyond the SELECT below.
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'mcp_ecommerce'@'localhost';

-- information_schema is read-only metadata and does not support GRANT.
-- MySQL exposes it implicitly to users for objects they can access.
GRANT SELECT ON `ecommerce_db`.* TO 'mcp_ecommerce'@'localhost';

SHOW GRANTS FOR 'mcp_ecommerce'@'localhost';

-- After execution, copy the generated password into:
-- %USERPROFILE%\.config\opencode\secrets\mysql-mcp-password
-- Write it as the only file content, without a trailing newline.
--
-- To rotate the password later:
-- ALTER USER 'mcp_ecommerce'@'localhost' IDENTIFIED BY RANDOM PASSWORD;
