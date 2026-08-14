-- =====================================================================
-- SLCP - Pruebas y diagramas generados
--
-- Se generan a partir de los requisitos aprobados y en cualquier orden: las
-- pruebas no necesitan los diagramas ni al reves. Ambos son propuestas, no
-- artefactos definitivos: nacen propuestos y alguien decide.
--
-- Se guarda su procedencia --- derivada o asistida --- porque no es lo mismo lo
-- que la plataforma dedujo del enunciado que lo que redacto un modelo. Quien
-- revise dentro de un ano tiene derecho a saber cual leia.
-- =====================================================================

CREATE TABLE generated_artifacts (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    project_id     UUID          NOT NULL,

    kind           VARCHAR(20)   NOT NULL,
    subkind        VARCHAR(40)   NOT NULL,
    title          VARCHAR(300)  NOT NULL,

    -- El contenido: codigo de prueba, o texto del diagrama en Mermaid.
    content        TEXT          NOT NULL,
    format         VARCHAR(20)   NOT NULL,

    origin         VARCHAR(20)   NOT NULL,
    -- De donde sale, para que quien revisa pueda juzgarlo sin adivinar.
    rationale      TEXT,
    -- Si contiene huecos que una persona debe rellenar (ANA-18).
    needs_decision BOOLEAN       NOT NULL DEFAULT FALSE,

    status         VARCHAR(20)   NOT NULL,
    version        INTEGER       NOT NULL DEFAULT 1,

    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    reviewed_by    UUID,
    reviewed_at    TIMESTAMPTZ,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_artifacts_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_artifacts_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_artifacts_creator  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_artifacts_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_artifacts_kind     CHECK (kind IN ('TEST','DIAGRAM')),
    CONSTRAINT ck_artifacts_origin   CHECK (origin IN ('DERIVED','ASSISTED','HUMAN')),
    CONSTRAINT ck_artifacts_status   CHECK (status IN ('PROPOSED','ACCEPTED','DISCARDED')),
    -- Lo aceptado consta de quien lo acepto: sin eso seria una afirmacion sin autor.
    CONSTRAINT ck_artifacts_review   CHECK (status <> 'ACCEPTED'
                                            OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE INDEX ix_artifacts_project ON generated_artifacts (project_id, kind);

-- ---------------------------------------------------------------------
-- Trazabilidad: que requisitos cubre cada artefacto (VER-03)
--
-- Sin este enlace no podria responderse que requisitos quedan sin prueba, que
-- es la pregunta que justifica generarlas.
-- ---------------------------------------------------------------------
CREATE TABLE artifact_requirements (
    artifact_id    UUID NOT NULL,
    requirement_id UUID NOT NULL,
    linked_at      TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (artifact_id, requirement_id),
    CONSTRAINT fk_ar_artifact    FOREIGN KEY (artifact_id) REFERENCES generated_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id)
);

CREATE INDEX ix_ar_requirement ON artifact_requirements (requirement_id);

-- ---------------------------------------------------------------------
-- Cobertura de pruebas por requisito
--
-- Calculada, no almacenada (PRG-10): un requisito esta cubierto cuando tiene al
-- menos una prueba aceptada. Con pruebas solo propuestas no lo esta --- nadie
-- las ha juzgado todavia --- y esa distincion es justamente la que interesa.
-- ---------------------------------------------------------------------
CREATE VIEW requirement_coverage AS
SELECT r.id                                                          AS requirement_id,
       COUNT(a.id) FILTER (WHERE a.kind = 'TEST')                    AS tests,
       COUNT(a.id) FILTER (WHERE a.kind = 'TEST' AND a.status = 'ACCEPTED') AS accepted_tests,
       (COUNT(a.id) FILTER (WHERE a.kind = 'TEST' AND a.status = 'ACCEPTED') > 0) AS covered
FROM requirements r
LEFT JOIN artifact_requirements ar ON ar.requirement_id = r.id
LEFT JOIN generated_artifacts a ON a.id = ar.artifact_id
GROUP BY r.id;

-- ---------------------------------------------------------------------
-- Solo se generan artefactos sobre requisitos aprobados
--
-- Generar pruebas de un requisito que aun puede cambiar produce pruebas que
-- habra que rehacer, y da por decidido lo que no lo esta.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_ar_requisito_aprobado()
RETURNS TRIGGER AS $$
DECLARE
    estado VARCHAR(20);
BEGIN
    SELECT status INTO estado FROM requirements WHERE id = NEW.requirement_id;

    IF estado <> 'APPROVED' THEN
        RAISE EXCEPTION 'VER-03: solo se generan pruebas y diagramas de requisitos aprobados; ese esta en %', estado
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_ar_requisito_aprobado
    BEFORE INSERT ON artifact_requirements
    FOR EACH ROW EXECUTE FUNCTION fn_ar_requisito_aprobado();
