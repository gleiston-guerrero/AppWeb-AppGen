-- =====================================================================
-- SLCP - Modificacion y eliminacion de proyectos y requisitos
--
-- Dos reglas de la parte interesada:
--   1. Modificar devuelve el elemento a su estado inicial.
--   2. Lo revisado o aprobado no se elimina.
--
-- La segunda tiene una consecuencia que conviene enunciar: eliminar un
-- requisito que alguien reviso o aprobo borraria la constancia de esa decision.
-- Por eso el borrado real solo se admite mientras nada se haya decidido; a
-- partir de ahi queda la anulacion, que conserva el rastro (ADM-01).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Marca de que el requisito llego alguna vez a revisarse o aprobarse.
--
-- No basta con mirar el estado actual: modificar un requisito aprobado lo
-- devuelve a borrador, y sin esta marca quedaria eliminable, con lo que la
-- regla se sortearia editando antes de borrar.
-- ---------------------------------------------------------------------
ALTER TABLE requirements ADD COLUMN ever_decided BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE requirements
SET ever_decided = TRUE
WHERE status IN ('REVIEWED', 'APPROVED', 'SUPERSEDED', 'ANNULLED')
   OR reviewed_by IS NOT NULL;

CREATE OR REPLACE FUNCTION fn_requirements_marcar_decidido()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('REVIEWED', 'APPROVED') THEN
        NEW.ever_decided := TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_requirements_marcar_decidido
    BEFORE INSERT OR UPDATE ON requirements
    FOR EACH ROW EXECUTE FUNCTION fn_requirements_marcar_decidido();

-- ---------------------------------------------------------------------
-- Lo decidido no se borra
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_requirements_borrado_restringido()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.ever_decided THEN
        RAISE EXCEPTION 'RQM-20: este requisito fue revisado o aprobado y no puede eliminarse. Anulelo si deja de exigirse: la anulacion conserva su historia'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_requirements_borrado_restringido
    BEFORE DELETE ON requirements
    FOR EACH ROW EXECUTE FUNCTION fn_requirements_borrado_restringido();

-- ---------------------------------------------------------------------
-- Un proyecto con requisitos no se borra
--
-- La clave foranea ya lo impide, pero con un mensaje que no dice que hacer. El
-- disparador lo explica.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_projects_borrado_restringido()
RETURNS TRIGGER AS $$
DECLARE
    cuantos INTEGER;
BEGIN
    SELECT COUNT(*) INTO cuantos FROM requirements WHERE project_id = OLD.id;
    IF cuantos > 0 THEN
        RAISE EXCEPTION 'PRJ-02: el proyecto tiene % requisitos y no puede eliminarse. Retirelo del servicio si ya no se trabaja en el', cuantos
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_projects_borrado_restringido
    BEFORE DELETE ON projects
    FOR EACH ROW EXECUTE FUNCTION fn_projects_borrado_restringido();
