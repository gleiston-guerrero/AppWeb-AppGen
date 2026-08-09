-- =====================================================================
-- SLCP - Invitaciones al equipo de un proyecto
--
-- Realiza INV-01 a INV-05 de SLCP-ADR-0005. Cubre el caso que hasta ahora no
-- tenia salida: incorporar a alguien que todavia no tiene cuenta.
-- =====================================================================

CREATE TABLE invitations (
    id             UUID          PRIMARY KEY,
    token_hash     VARCHAR(64)   NOT NULL,
    project_id     UUID          NOT NULL,
    email          VARCHAR(254)  NOT NULL,
    project_role   VARCHAR(20)   NOT NULL,
    invited_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    expires_at     TIMESTAMPTZ   NOT NULL,
    consumed_at    TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(200),

    CONSTRAINT uq_invitations_token   UNIQUE (token_hash),
    CONSTRAINT fk_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_invitations_inviter FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT ck_invitations_role    CHECK (project_role IN ('PROJECT_FACILITATOR', 'TEAM_MEMBER', 'PRODUCT_OWNER')),
    CONSTRAINT ck_invitations_lower   CHECK (email = LOWER(email)),
    CONSTRAINT ck_invitations_plazo   CHECK (expires_at > created_at)
);

CREATE INDEX ix_invitations_project ON invitations (project_id);
CREATE INDEX ix_invitations_email   ON invitations (email);

-- Una invitacion vigente por correo y proyecto. Sin esto, invitar dos veces a
-- la misma persona produciria dos enlaces validos y un rastro ambiguo.
CREATE UNIQUE INDEX uq_invitations_vigente
    ON invitations (project_id, email)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- Una invitacion consumida o revocada no vuelve atras
--
-- Es la misma regla que gobierna los tokens de sesion: si un enlace pudiera
-- volver a estar vigente, revocarlo no revocaria nada.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_invitations_sin_marcha_atras()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS NULL THEN
        RAISE EXCEPTION 'INV-01: una invitacion consumida no puede volver a estar vigente'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'INV-01: una invitacion revocada no puede volver a estar vigente'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_invitations_sin_marcha_atras
    BEFORE UPDATE ON invitations
    FOR EACH ROW EXECUTE FUNCTION fn_invitations_sin_marcha_atras();
