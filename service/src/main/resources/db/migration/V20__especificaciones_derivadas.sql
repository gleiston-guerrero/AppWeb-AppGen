-- =====================================================================
-- SLCP - Casos de uso expandidos e historias de usuario
--
-- Campos segun las tablas 8 y 9 del manuscrito, derivadas de revision
-- sistematica: seis obligatorios en casos de uso y dos en historias.
--
-- Se generan con IA, se editan, y lo que el equipo acepta pasa a ser REGLA
-- BASE: se conserva aunque se vuelva a generar. Regenerar sobre lo aceptado
-- borraria el trabajo de quien lo reviso, y entonces nadie revisaria nada.
--
-- La excepcion es que cambien los requisitos de los que salio. Para poder
-- saberlo se guarda la version del requisito en el momento de aceptarla: si la
-- actual es otra, lo aceptado se refiere a un texto que ya no rige.
-- =====================================================================

CREATE TABLE specifications (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    project_id     UUID          NOT NULL,

    kind           VARCHAR(20)   NOT NULL,
    name           VARCHAR(300)  NOT NULL,

    -- Campos segun la clase. Se guardan como documento porque los flujos son
    -- listas de pasos con dos columnas y las historias tienen nueve campos
    -- opcionales: una tabla por campo daria veinte columnas casi siempre nulas.
    fields         JSONB         NOT NULL,

    origin         VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,

    -- Regla base: aceptada por el equipo y conservada al regenerar.
    is_baseline    BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Se marca cuando cambia algun requisito del que salio.
    stale          BOOLEAN       NOT NULL DEFAULT FALSE,

    version        INTEGER       NOT NULL DEFAULT 1,
    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    accepted_by    UUID,
    accepted_at    TIMESTAMPTZ,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_spec_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_spec_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_spec_creator  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_spec_accepter FOREIGN KEY (accepted_by) REFERENCES users (id),
    CONSTRAINT ck_spec_kind     CHECK (kind IN ('USE_CASE','USER_STORY')),
    CONSTRAINT ck_spec_origin   CHECK (origin IN ('AI_GENERATED','AI_EDITED','HUMAN')),
    CONSTRAINT ck_spec_status   CHECK (status IN ('DRAFT','PROPOSED','ACCEPTED','DISCARDED')),
    -- Lo aceptado consta de quien y cuando: sin eso, la regla base seria una
    -- afirmacion sin autor.
    CONSTRAINT ck_spec_accept   CHECK (status <> 'ACCEPTED'
                                       OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)),
    -- Solo lo aceptado es regla base.
    CONSTRAINT ck_spec_baseline CHECK (NOT is_baseline OR status = 'ACCEPTED')
);

CREATE INDEX ix_spec_project ON specifications (project_id, kind);

-- ---------------------------------------------------------------------
-- Requisitos de los que sale, con la version que tenian al aceptarse
--
-- La version es la clave de todo: sin ella no puede distinguirse una regla base
-- que sigue valiendo de otra que se refiere a un requisito ya modificado, y
-- ambas se verian igual de firmes.
-- ---------------------------------------------------------------------
CREATE TABLE specification_requirements (
    specification_id     UUID        NOT NULL,
    requirement_id       UUID        NOT NULL,
    -- Version del requisito cuando se enlazo o se acepto.
    requirement_version  INTEGER     NOT NULL,
    linked_at            TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (specification_id, requirement_id),
    CONSTRAINT fk_sr_spec        FOREIGN KEY (specification_id) REFERENCES specifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_sr_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id)
);

CREATE INDEX ix_sr_requirement ON specification_requirements (requirement_id);

-- ---------------------------------------------------------------------
-- Reparos de la comprobacion al guardar
-- ---------------------------------------------------------------------
CREATE TABLE specification_issues (
    id               UUID         PRIMARY KEY,
    specification_id UUID         NOT NULL,
    field            VARCHAR(60)  NOT NULL,
    reason           TEXT         NOT NULL,
    severe           BOOLEAN      NOT NULL DEFAULT FALSE,
    source           VARCHAR(20)  NOT NULL,
    raised_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_si_spec   FOREIGN KEY (specification_id) REFERENCES specifications (id) ON DELETE CASCADE,
    CONSTRAINT ck_si_source CHECK (source IN ('RULE','ASSISTED'))
);

CREATE INDEX ix_si_spec ON specification_issues (specification_id);

-- ---------------------------------------------------------------------
-- Que reglas base se quedaron atras (PRG-10: calculado, no almacenado)
--
-- Una regla base esta atrasada cuando la version actual de alguno de sus
-- requisitos es distinta de la que tenia al aceptarse.
-- ---------------------------------------------------------------------
CREATE VIEW baseline_freshness AS
SELECT s.id                                                   AS specification_id,
       s.readable_id,
       COUNT(sr.requirement_id)                               AS requirements,
       COUNT(*) FILTER (WHERE r.version <> sr.requirement_version) AS changed,
       (COUNT(*) FILTER (WHERE r.version <> sr.requirement_version) > 0) AS outdated
FROM specifications s
LEFT JOIN specification_requirements sr ON sr.specification_id = s.id
LEFT JOIN requirements r ON r.id = sr.requirement_id
WHERE s.is_baseline
GROUP BY s.id, s.readable_id;
