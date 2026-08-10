-- =====================================================================
-- SLCP - Recuperacion de acceso
--
-- Del enlace se guarda solo su resumen, como en los tokens de sesion y en las
-- invitaciones: quien lea la tabla no obtiene nada utilizable.
-- =====================================================================

CREATE TABLE password_resets (
    id           UUID          PRIMARY KEY,
    token_hash   VARCHAR(64)   NOT NULL,
    user_id      UUID          NOT NULL,
    requested_at TIMESTAMPTZ   NOT NULL,
    expires_at   TIMESTAMPTZ   NOT NULL,
    used_at      TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    origin       VARCHAR(60)   NOT NULL,

    CONSTRAINT uq_password_resets_token UNIQUE (token_hash),
    CONSTRAINT fk_password_resets_user  FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_password_resets_plazo CHECK (expires_at > requested_at)
);

CREATE INDEX ix_password_resets_user ON password_resets (user_id);

-- ---------------------------------------------------------------------
-- Un enlace usado o revocado no vuelve a servir.
--
-- Es la misma regla que gobierna las invitaciones y los tokens de sesion. Si
-- un enlace de recuperacion pudiera volver a estar vigente, revocarlo tras un
-- incidente no revocaria nada.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_password_resets_sin_marcha_atras()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.used_at IS NOT NULL AND NEW.used_at IS NULL)
       OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'SEC-06: un enlace de recuperacion usado o revocado no puede volver a estar vigente'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_password_resets_sin_marcha_atras
    BEFORE UPDATE ON password_resets
    FOR EACH ROW EXECUTE FUNCTION fn_password_resets_sin_marcha_atras();
