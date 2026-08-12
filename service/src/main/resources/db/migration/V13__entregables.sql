-- =====================================================================
-- SLCP - Entregables (WBS, nivel 2)
--
-- Primer nivel de la descomposicion del trabajo definida en SLCP-DOC-018.
-- Un entregable es un resultado que alguien recibe y acepta, y realiza uno o
-- varios requisitos aprobados.
--
-- Con esta migracion el cierre de un requisito pasa a ser calculable (RQM-14):
-- un requisito se cierra cuando todos sus entregables han sido aceptados.
-- =====================================================================

CREATE TABLE deliverables (
    id            UUID          PRIMARY KEY,
    readable_id   VARCHAR(40)   NOT NULL,
    project_id    UUID          NOT NULL,

    name          VARCHAR(300)  NOT NULL,
    description   TEXT,
    acceptance    TEXT,

    status        VARCHAR(20)   NOT NULL,
    version       INTEGER       NOT NULL DEFAULT 1,

    -- Marca de que llego alguna vez a entregarse o aceptarse. Modificar un
    -- entregable lo devuelve a planificado, y sin esta marca quedaria
    -- eliminable, con lo que la regla se sortearia editando antes de borrar.
    ever_decided  BOOLEAN       NOT NULL DEFAULT FALSE,

    accepted_by   UUID,
    accepted_at   TIMESTAMPTZ,

    created_by    UUID          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    lock_version  INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_deliverables_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_deliverables_project  FOREIGN KEY (project_id)  REFERENCES projects (id),
    CONSTRAINT fk_deliverables_creator  FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_deliverables_accepter FOREIGN KEY (accepted_by) REFERENCES users (id),
    CONSTRAINT ck_deliverables_status   CHECK (status IN ('PLANNED','IN_PROGRESS','DELIVERED','ACCEPTED','REJECTED')),
    -- Un entregable aceptado ha de constar quien lo acepto y cuando: sin eso,
    -- la aceptacion seria una afirmacion sin autor.
    CONSTRAINT ck_deliverables_acepta   CHECK (status <> 'ACCEPTED' OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL))
);

CREATE INDEX ix_deliverables_project ON deliverables (project_id);
CREATE INDEX ix_deliverables_status  ON deliverables (project_id, status);

-- ---------------------------------------------------------------------
-- Enlace con los requisitos que realiza (WBS-07)
--
-- Varios a varios: un entregable suele realizar mas de un requisito, y forzar
-- uno a uno obligaria a partir entregables por razones de registro.
-- ---------------------------------------------------------------------
CREATE TABLE deliverable_requirements (
    deliverable_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    linked_at      TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (deliverable_id, requirement_id),
    CONSTRAINT fk_dr_deliverable FOREIGN KEY (deliverable_id) REFERENCES deliverables (id) ON DELETE CASCADE,
    CONSTRAINT fk_dr_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id)
);

CREATE INDEX ix_dr_requirement ON deliverable_requirements (requirement_id);

-- ---------------------------------------------------------------------
-- Solo se enlazan requisitos aprobados
--
-- Enlazar trabajo a un requisito sin aprobar significaria construir sobre algo
-- que aun puede cambiar, y el enlace daria por decidido lo que no lo esta.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_dr_requisito_aprobado()
RETURNS TRIGGER AS $$
DECLARE
    estado VARCHAR(20);
    proyecto_req UUID;
    proyecto_ent UUID;
BEGIN
    SELECT status, project_id INTO estado, proyecto_req
    FROM requirements WHERE id = NEW.requirement_id;

    IF estado <> 'APPROVED' THEN
        RAISE EXCEPTION 'WBS-07: solo se enlaza trabajo a requisitos aprobados; ese esta en %', estado
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT project_id INTO proyecto_ent FROM deliverables WHERE id = NEW.deliverable_id;

    IF proyecto_req <> proyecto_ent THEN
        RAISE EXCEPTION 'RPT-04: el requisito pertenece a otro proyecto'
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_dr_requisito_aprobado
    BEFORE INSERT ON deliverable_requirements
    FOR EACH ROW EXECUTE FUNCTION fn_dr_requisito_aprobado();

-- ---------------------------------------------------------------------
-- Lo entregado o aceptado no se borra; y la marca se pone sola
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_deliverables_marcar_decidido()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('DELIVERED','ACCEPTED') THEN
        NEW.ever_decided := TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_deliverables_marcar_decidido
    BEFORE INSERT OR UPDATE ON deliverables
    FOR EACH ROW EXECUTE FUNCTION fn_deliverables_marcar_decidido();

CREATE OR REPLACE FUNCTION fn_deliverables_borrado_restringido()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.ever_decided THEN
        RAISE EXCEPTION 'WBS-08: este entregable fue entregado o aceptado y no puede eliminarse'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_deliverables_borrado_restringido
    BEFORE DELETE ON deliverables
    FOR EACH ROW EXECUTE FUNCTION fn_deliverables_borrado_restringido();

-- ---------------------------------------------------------------------
-- El cierre del requisito se calcula, no se declara (RQM-14)
--
-- Se expone como vista para que no exista forma de almacenarlo. Lo que se
-- calcula no puede quedar desactualizado; lo que se guarda, si.
-- ---------------------------------------------------------------------
CREATE VIEW requirement_closure AS
SELECT r.id                                   AS requirement_id,
       COUNT(dr.deliverable_id)               AS deliverables,
       COUNT(*) FILTER (WHERE d.status = 'ACCEPTED') AS accepted,
       (COUNT(dr.deliverable_id) > 0
        AND COUNT(*) FILTER (WHERE d.status = 'ACCEPTED') = COUNT(dr.deliverable_id)
        AND r.status = 'APPROVED')            AS closed
FROM requirements r
LEFT JOIN deliverable_requirements dr ON dr.requirement_id = r.id
LEFT JOIN deliverables d ON d.id = dr.deliverable_id
GROUP BY r.id, r.status;
