-- =====================================================================
-- SLCP - Esquema inicial
--
-- Realiza: TRC-03 (identificacion estable), TRC-04 (versionado inmutable),
--          TRC-24 (almacen de solo anexado), FUN-15 (autorregistro).
--
-- Convenciones de nomenclatura conforme a NAM: snake_case en minusculas,
-- tablas en plural, columnas en singular, restricciones con prefijo de tipo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Cuentas de usuario
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID          PRIMARY KEY,
    readable_id         VARCHAR(40)   NOT NULL,
    username            VARCHAR(60)   NOT NULL,
    email               VARCHAR(254)  NOT NULL,
    full_name           VARCHAR(160)  NOT NULL,
    password_verifier   VARCHAR(255),
    status              VARCHAR(20)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    version             INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_readable_id UNIQUE (readable_id),
    CONSTRAINT uq_users_username    UNIQUE (username),
    CONSTRAINT uq_users_email       UNIQUE (email),
    CONSTRAINT ck_users_status      CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'DECOMMISSIONED'))
);

-- FUN-03 exige que ni un nombre de usuario coincida con el correo de otra
-- cuenta, de modo que la resolucion entre ambos identificadores nunca sea
-- ambigua. Se comprueba con un indice sobre la union de ambos valores.
CREATE UNIQUE INDEX uq_users_login_identifier
    ON users (LOWER(username));

CREATE UNIQUE INDEX uq_users_email_lower
    ON users (LOWER(email));

-- ---------------------------------------------------------------------
-- Almacen de solo anexado (TRC-24)
--
-- No lleva clave foranea a proposito: un evento debe poder sobrevivir a la
-- desaparicion de cualquier otra fila. Tampoco tiene columna de modificacion:
-- no existe operacion que actualice un evento.
-- ---------------------------------------------------------------------
CREATE TABLE event_records (
    id              UUID          PRIMARY KEY,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    event_type      VARCHAR(60)   NOT NULL,
    subject_type    VARCHAR(60)   NOT NULL,
    subject_id      UUID          NOT NULL,
    actor_id        UUID,
    actor_label     VARCHAR(160)  NOT NULL,
    payload         TEXT          NOT NULL
);

CREATE INDEX ix_event_records_subject   ON event_records (subject_type, subject_id);
CREATE INDEX ix_event_records_occurred  ON event_records (occurred_at);
