-- =====================================================================
-- SLCP - Historial de decisiones sobre requisitos
--
-- Cuando el propietario no aprueba un requisito y este se modifica, la
-- revision anterior se olvida: lo revisado era otro texto. Pero olvidarla en la
-- fila no debe significar perderla, porque entonces no puede responderse quien
-- reviso la version anterior ni por que se reproba.
--
-- Cada decision queda aqui con la version sobre la que se tomo.
-- =====================================================================

CREATE TABLE requirement_decisions (
    id             UUID          PRIMARY KEY,
    requirement_id UUID          NOT NULL,
    version        INTEGER       NOT NULL,
    decision       VARCHAR(20)   NOT NULL,
    actor_id       UUID          NOT NULL,
    actor_label    VARCHAR(120)  NOT NULL,
    decided_at     TIMESTAMPTZ   NOT NULL,
    statement      TEXT          NOT NULL,

    CONSTRAINT fk_rd_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE,
    CONSTRAINT fk_rd_actor       FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_rd_decision    CHECK (decision IN ('REVIEWED','APPROVED','REJECTED','ANNULLED'))
);

CREATE INDEX ix_rd_requirement ON requirement_decisions (requirement_id, version);

-- El enunciado se guarda con la decision, no solo su referencia: quien aprobo
-- lo hizo sobre un texto concreto, y si ese texto cambia despues, la decision
-- seguiria apuntando a uno que nadie leyo.
COMMENT ON COLUMN requirement_decisions.statement IS
    'Texto sobre el que se tomo la decision, tal como estaba entonces';
