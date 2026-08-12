-- =====================================================================
-- SLCP - El propietario del producto no acumula otros roles (ROL-06)
--
-- Hasta aqui la incompatibilidad alcanzaba solo al miembro del equipo. Se
-- extiende al facilitador: este da la revision previa de RQM-05 y el
-- propietario la aprobacion definitiva, de modo que acumular ambos dejaria las
-- dos etapas en manos de una sola persona.
--
-- Facilitador y miembro del equipo siguen siendo compatibles: organizar y
-- ejecutar no se vigilan mutuamente.
-- =====================================================================

-- El indice anterior no sirve para esta regla: prohibia dos filas de un mismo
-- par cuando el rol era de produccion o de aprobacion, lo que ademas impedia
-- que el facilitador fuese tambien miembro del equipo si se ampliaba su lista.
DROP INDEX IF EXISTS uq_memberships_segregacion;

CREATE OR REPLACE FUNCTION fn_memberships_propietario_exclusivo()
RETURNS TRIGGER AS $$
DECLARE
    otros INTEGER;
BEGIN
    IF NEW.status = 'DECOMMISSIONED' THEN
        RETURN NEW;
    END IF;

    IF NEW.project_role = 'PRODUCT_OWNER' THEN
        SELECT COUNT(*) INTO otros
        FROM project_memberships
        WHERE project_id = NEW.project_id
          AND user_id = NEW.user_id
          AND id <> NEW.id
          AND status <> 'DECOMMISSIONED';

        IF otros > 0 THEN
            RAISE EXCEPTION 'ROL-06: el propietario del producto no puede acumular otro rol en el mismo proyecto'
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        SELECT COUNT(*) INTO otros
        FROM project_memberships
        WHERE project_id = NEW.project_id
          AND user_id = NEW.user_id
          AND id <> NEW.id
          AND project_role = 'PRODUCT_OWNER'
          AND status <> 'DECOMMISSIONED';

        IF otros > 0 THEN
            RAISE EXCEPTION 'ROL-06: esa persona es propietaria del producto en este proyecto y no puede acumular otro rol'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_memberships_propietario_exclusivo
    BEFORE INSERT OR UPDATE ON project_memberships
    FOR EACH ROW EXECUTE FUNCTION fn_memberships_propietario_exclusivo();

-- Un mismo rol no se repite para la misma persona en el mismo proyecto.
CREATE UNIQUE INDEX uq_memberships_rol_unico
    ON project_memberships (project_id, user_id, project_role)
    WHERE status <> 'DECOMMISSIONED';
