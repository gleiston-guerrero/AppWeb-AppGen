-- =====================================================================
-- SLCP - Proyectos, equipo y roles por proyecto
--
-- Hasta aqui el rol de plataforma solo distinguia al administrador. Los demas
-- roles son por proyecto, conforme a ROL-01, y necesitan proyectos que no
-- existian. Esta migracion los crea.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. El rol de plataforma expresa que puede hacerse SIN proyecto
--
-- Quien se autorregistra obtiene la capacidad de crear proyectos (FUN-15).
-- Quien llega por invitacion no la obtiene: su alcance es el proyecto que
-- origino su cuenta.
-- ---------------------------------------------------------------------
-- El orden importa y no es indiferente: primero se retira la restriccion vieja,
-- despues se migran los datos, y solo entonces se impone la nueva. Imponerla
-- antes de migrar falla contra cualquier base que ya tenga cuentas, que es
-- precisamente el caso para el que esta migracion existe.
ALTER TABLE users DROP CONSTRAINT ck_users_platform_role;

-- El valor por defecto tambien depende de la restriccion, de modo que se retira
-- antes de tocar los datos y se fija al final.
ALTER TABLE users ALTER COLUMN platform_role DROP DEFAULT;

UPDATE users SET platform_role = 'FACILITATOR' WHERE platform_role = 'USER';

ALTER TABLE users
    ADD CONSTRAINT ck_users_platform_role
    CHECK (platform_role IN ('MEMBER', 'FACILITATOR', 'ADMINISTRATOR'));

ALTER TABLE users ALTER COLUMN platform_role SET DEFAULT 'MEMBER';

-- ---------------------------------------------------------------------
-- 2. Proyectos
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    name           VARCHAR(160)  NOT NULL,
    purpose        VARCHAR(1000) NOT NULL DEFAULT '',
    status         VARCHAR(20)   NOT NULL,
    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    version        INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_projects_readable_id UNIQUE (readable_id),
    CONSTRAINT fk_projects_creator     FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_projects_status      CHECK (status IN ('ACTIVE', 'DECOMMISSIONED'))
);

CREATE INDEX ix_projects_creator ON projects (created_by);

-- ---------------------------------------------------------------------
-- 3. Membresias
--
-- La membresia es la unidad de autorizacion: el rol se resuelve respecto del
-- proyecto sobre el que se actua, nunca respecto de la cuenta.
-- ---------------------------------------------------------------------
CREATE TABLE project_memberships (
    id            UUID         PRIMARY KEY,
    project_id    UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    project_role  VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_memberships_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_memberships_user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT ck_memberships_role    CHECK (project_role IN ('PROJECT_FACILITATOR', 'TEAM_MEMBER', 'PRODUCT_OWNER')),
    CONSTRAINT ck_memberships_status  CHECK (status IN ('INVITED', 'ACTIVE', 'DECOMMISSIONED')),
    CONSTRAINT uq_memberships_unica   UNIQUE (project_id, user_id, project_role)
);

CREATE INDEX ix_memberships_project ON project_memberships (project_id);
CREATE INDEX ix_memberships_user    ON project_memberships (user_id);

-- ROL-06: nadie puede ser a la vez quien produce y quien aprueba en el mismo
-- proyecto. Se impone aqui y no solo en el dominio porque es una segregacion
-- que debe cumplirse por cualquier via de acceso: un control que puede
-- sortearse con una consulta suelta no es un control.
CREATE UNIQUE INDEX uq_memberships_segregacion
    ON project_memberships (project_id, user_id)
    WHERE project_role IN ('TEAM_MEMBER', 'PRODUCT_OWNER')
      AND status <> 'DECOMMISSIONED';

-- ---------------------------------------------------------------------
-- 4. Lo inmutable, inmutable
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_projects_campos_inmutables()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'TRC-03: el identificador y la fecha de creacion de un proyecto no pueden modificarse'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_projects_inmutables
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION fn_projects_campos_inmutables();
