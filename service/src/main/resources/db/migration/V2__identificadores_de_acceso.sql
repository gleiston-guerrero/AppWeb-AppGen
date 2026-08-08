-- =====================================================================
-- SLCP - Identificadores de acceso
--
-- Corrige un defecto detectado en la verificacion de V1: las restricciones
-- de unicidad sobre 'username' y sobre 'email' eran independientes, de modo
-- que se aceptaba una cuenta cuyo nombre de usuario coincidiera con el correo
-- de otra. FUN-03 exige que la resolucion entre ambos identificadores nunca
-- sea ambigua, y aquello no lo garantizaba.
--
-- La solucion es una tabla de identificadores de acceso: ambos valores viven
-- en el mismo espacio de nombres, y su unicidad la impone la clave primaria.
-- =====================================================================

CREATE TABLE login_identifiers (
    identifier  VARCHAR(254) PRIMARY KEY,
    user_id     UUID          NOT NULL,
    kind        VARCHAR(10)   NOT NULL,

    CONSTRAINT fk_login_identifiers_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_login_identifiers_kind
        CHECK (kind IN ('USERNAME', 'EMAIL')),
    CONSTRAINT ck_login_identifiers_lowercase
        CHECK (identifier = LOWER(identifier))
);

CREATE INDEX ix_login_identifiers_user ON login_identifiers (user_id);

-- Traslado de las cuentas ya existentes.
INSERT INTO login_identifiers (identifier, user_id, kind)
SELECT LOWER(username), id, 'USERNAME' FROM users;

INSERT INTO login_identifiers (identifier, user_id, kind)
SELECT LOWER(email), id, 'EMAIL' FROM users;
