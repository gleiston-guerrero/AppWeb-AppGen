-- =====================================================================
-- SLCP - Requisitos
--
-- Realiza RQM-01 a RQM-19 y DOC-017. Los requisitos pertenecen a un proyecto y
-- su alcance se deriva de la membresia, como todo lo demas.
-- =====================================================================

CREATE TABLE requirements (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    project_id     UUID          NOT NULL,

    -- Identificador tal como figura en el documento de origen, si lo hubo.
    source_id      VARCHAR(40),
    source_line    INTEGER,

    kind           VARCHAR(20)   NOT NULL,
    name           VARCHAR(300),
    statement      TEXT          NOT NULL,
    verification   TEXT,
    priority       VARCHAR(20),
    actor          VARCHAR(200),
    notes          TEXT,

    status         VARCHAR(20)   NOT NULL,
    version        INTEGER       NOT NULL DEFAULT 1,

    -- Procedencia del texto: escrito por una persona o aceptado de una
    -- sugerencia de la plataforma (ANA-16).
    statement_origin    VARCHAR(20) NOT NULL DEFAULT 'HUMAN',
    verification_origin VARCHAR(20) NOT NULL DEFAULT 'HUMAN',

    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_requirements_readable   UNIQUE (project_id, readable_id),
    CONSTRAINT fk_requirements_project    FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_requirements_creator    FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_requirements_kind       CHECK (kind IN ('FUNCTIONAL','NON_FUNCTIONAL','CONSTRAINT','USER_STORY','USE_CASE','OTHER')),
    CONSTRAINT ck_requirements_status     CHECK (status IN ('DRAFT','REVIEWED','APPROVED','REJECTED','SUPERSEDED','ANNULLED')),
    CONSTRAINT ck_requirements_origin     CHECK (statement_origin IN ('HUMAN','SUGGESTED') AND verification_origin IN ('HUMAN','SUGGESTED'))
);

CREATE INDEX ix_requirements_project ON requirements (project_id);
CREATE INDEX ix_requirements_status  ON requirements (project_id, status);

-- El identificador de origen, cuando existe, no puede repetirse dentro del
-- proyecto: dos requisitos con el mismo RF-01 harian ambigua toda referencia.
CREATE UNIQUE INDEX uq_requirements_source
    ON requirements (project_id, source_id)
    WHERE source_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Lo inmutable (TRC-03) y la aprobacion que no se hereda (RQM-19)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_requirements_reglas()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.project_id IS DISTINCT FROM OLD.project_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'TRC-03: identificador, proyecto y fecha de creacion de un requisito son inmutables'
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- RQM-08: cambiar el enunciado de un requisito aprobado lo devuelve a
    -- revision. Lo aprobado fue un texto concreto, no el hueco que ocupaba.
    IF OLD.status = 'APPROVED'
       AND (NEW.statement IS DISTINCT FROM OLD.statement
            OR NEW.verification IS DISTINCT FROM OLD.verification)
       AND NEW.status = 'APPROVED' THEN
        RAISE EXCEPTION 'RQM-08: no puede modificarse el texto de un requisito aprobado sin devolverlo a revision'
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_requirements_reglas
    BEFORE UPDATE ON requirements
    FOR EACH ROW EXECUTE FUNCTION fn_requirements_reglas();
