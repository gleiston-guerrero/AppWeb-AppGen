-- =====================================================================
-- SLCP - Componentes, tareas, actividades y recursos
--
-- Completa la descomposicion de SLCP-DOC-018 por debajo del entregable:
--   entregable -> componente -> tarea -> actividad
--
-- El avance no se guarda en ninguna parte. Se calcula de abajo arriba y se
-- pondera por el esfuerzo previsto (PRG-06, PRG-07, PRG-10): un porcentaje
-- escrito a mano es una opinion con apariencia de medida, y su defecto conocido
-- es estancarse cerca del noventa por ciento durante media obra.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Componentes
-- ---------------------------------------------------------------------
CREATE TABLE components (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    deliverable_id UUID          NOT NULL,
    project_id     UUID          NOT NULL,

    name           VARCHAR(300)  NOT NULL,
    description    TEXT,

    ever_decided   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_components_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_components_deliverable FOREIGN KEY (deliverable_id) REFERENCES deliverables (id),
    CONSTRAINT fk_components_project     FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_components_creator     FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX ix_components_deliverable ON components (deliverable_id);

-- ---------------------------------------------------------------------
-- Tareas
--
-- WBS-06: cuelgan siempre de un componente. El esfuerzo previsto es
-- obligatorio porque sin el, el avance del componente tendria que promediarse
-- sin peso, y terminar cinco tareas triviales dejando la dificil daria un
-- ochenta y tres por ciento.
--
-- Se asignan a una sola persona: con varias, la carga por persona deja de poder
-- calcularse y nadie responde de la tarea.
-- ---------------------------------------------------------------------
CREATE TABLE tasks (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    component_id   UUID          NOT NULL,
    project_id     UUID          NOT NULL,

    name           VARCHAR(300)  NOT NULL,
    description    TEXT,
    planned_effort INTEGER       NOT NULL,

    assignee_id    UUID,
    status         VARCHAR(20)   NOT NULL,
    done_at        TIMESTAMPTZ,
    done_by        UUID,

    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_tasks_readable   UNIQUE (project_id, readable_id),
    CONSTRAINT fk_tasks_component  FOREIGN KEY (component_id) REFERENCES components (id),
    CONSTRAINT fk_tasks_project    FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_tasks_assignee   FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_creator    FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_tasks_doer       FOREIGN KEY (done_by) REFERENCES users (id),
    CONSTRAINT ck_tasks_status     CHECK (status IN ('PENDING','IN_PROGRESS','DONE','BLOCKED')),
    CONSTRAINT ck_tasks_effort     CHECK (planned_effort > 0),
    -- PRG-08: una tarea terminada consta de quien la dio por hecha y cuando.
    CONSTRAINT ck_tasks_done       CHECK (status <> 'DONE' OR (done_by IS NOT NULL AND done_at IS NOT NULL))
);

CREATE INDEX ix_tasks_component ON tasks (component_id);
CREATE INDEX ix_tasks_assignee  ON tasks (assignee_id) WHERE assignee_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Actividades
--
-- Ultimo nivel: el unico que registra hechos. Lleva el esfuerzo previsto de la
-- propia actividad, para poder ponderar dentro de la tarea, y las horas
-- realmente dedicadas, que son cosa distinta (PRG-09).
-- ---------------------------------------------------------------------
CREATE TABLE activities (
    id             UUID          PRIMARY KEY,
    readable_id    VARCHAR(40)   NOT NULL,
    task_id        UUID          NOT NULL,
    project_id     UUID          NOT NULL,

    name           VARCHAR(300)  NOT NULL,
    planned_effort INTEGER       NOT NULL DEFAULT 1,

    done           BOOLEAN       NOT NULL DEFAULT FALSE,
    done_at        TIMESTAMPTZ,

    created_by     UUID          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_activities_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_activities_task     FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_activities_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_activities_creator  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_activities_effort   CHECK (planned_effort > 0),
    CONSTRAINT ck_activities_done     CHECK (NOT done OR done_at IS NOT NULL)
);

CREATE INDEX ix_activities_task ON activities (task_id);

-- ---------------------------------------------------------------------
-- Dedicacion
--
-- Las horas se registran como asientos y no como un total en la actividad: un
-- total se sobrescribe y pierde cuando se dedico cada hora, que es lo que hace
-- falta para responder si lo hecho corresponde a lo gastado.
-- ---------------------------------------------------------------------
CREATE TABLE time_entries (
    id           UUID         PRIMARY KEY,
    activity_id  UUID         NOT NULL,
    project_id   UUID         NOT NULL,

    person_id    UUID         NOT NULL,
    hours        NUMERIC(6,2) NOT NULL,
    worked_on    DATE         NOT NULL,
    note         TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_time_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
    CONSTRAINT fk_time_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_time_person   FOREIGN KEY (person_id) REFERENCES users (id),
    CONSTRAINT ck_time_hours    CHECK (hours > 0 AND hours <= 24)
);

CREATE INDEX ix_time_activity ON time_entries (activity_id);
CREATE INDEX ix_time_person   ON time_entries (person_id, worked_on);

-- ---------------------------------------------------------------------
-- Recursos materiales
--
-- Las personas no se catalogan aqui: ya son miembros del equipo, y tenerlas en
-- dos sitios acabaria con dos listas que discrepan.
-- ---------------------------------------------------------------------
CREATE TABLE resources (
    id           UUID          PRIMARY KEY,
    readable_id  VARCHAR(40)   NOT NULL,
    project_id   UUID          NOT NULL,

    name         VARCHAR(300)  NOT NULL,
    kind         VARCHAR(20)   NOT NULL,
    unit         VARCHAR(40),
    quantity     NUMERIC(10,2),
    notes        TEXT,

    created_by   UUID          NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT uq_resources_readable UNIQUE (project_id, readable_id),
    CONSTRAINT fk_resources_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_resources_creator  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_resources_kind     CHECK (kind IN ('EQUIPMENT','SOFTWARE','FACILITY','CONSUMABLE','SERVICE','OTHER'))
);

CREATE TABLE task_resources (
    task_id     UUID         NOT NULL,
    resource_id UUID         NOT NULL,
    quantity    NUMERIC(10,2),
    assigned_at TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (task_id, resource_id),
    CONSTRAINT fk_tr_task     FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_resource FOREIGN KEY (resource_id) REFERENCES resources (id)
);

-- ---------------------------------------------------------------------
-- Avance calculado (PRG-06, PRG-07, PRG-10)
--
-- Cuatro vistas encadenadas. Ninguna guarda nada: el avance de un elemento
-- compuesto sale siempre de sus hijos, ponderado por el esfuerzo previsto.
-- ---------------------------------------------------------------------

-- Tarea: proporcion del esfuerzo de sus actividades que esta hecha.
-- Sin actividades, una tarea dada por hecha vale uno y las demas cero: no hay
-- de donde derivar mas, y suponer un avance intermedio seria inventarlo.
CREATE VIEW task_progress AS
SELECT t.id                                       AS task_id,
       t.planned_effort                           AS effort,
       COALESCE(SUM(a.planned_effort), 0)         AS activity_effort,
       COALESCE(SUM(a.planned_effort) FILTER (WHERE a.done), 0) AS activity_done,
       CASE
           WHEN t.status = 'DONE' THEN 1.0
           WHEN COALESCE(SUM(a.planned_effort), 0) = 0 THEN 0.0
           ELSE COALESCE(SUM(a.planned_effort) FILTER (WHERE a.done), 0)::NUMERIC
                / SUM(a.planned_effort)
       END                                        AS progress,
       COALESCE((SELECT SUM(te.hours) FROM time_entries te
                 JOIN activities a2 ON a2.id = te.activity_id
                 WHERE a2.task_id = t.id), 0)     AS spent_hours
FROM tasks t
LEFT JOIN activities a ON a.task_id = t.id
GROUP BY t.id, t.planned_effort, t.status;

-- Componente: ponderado por el esfuerzo previsto de sus tareas.
CREATE VIEW component_progress AS
SELECT c.id                                    AS component_id,
       COALESCE(SUM(tp.effort), 0)             AS effort,
       CASE WHEN COALESCE(SUM(tp.effort), 0) = 0 THEN 0.0
            ELSE SUM(tp.progress * tp.effort) / SUM(tp.effort) END AS progress,
       COALESCE(SUM(tp.spent_hours), 0)        AS spent_hours,
       COUNT(tp.task_id)                       AS tasks
FROM components c
LEFT JOIN tasks t ON t.component_id = c.id
LEFT JOIN task_progress tp ON tp.task_id = t.id
GROUP BY c.id;

-- Entregable: ponderado por el esfuerzo de sus componentes.
CREATE VIEW deliverable_progress AS
SELECT d.id                                    AS deliverable_id,
       COALESCE(SUM(cp.effort), 0)             AS effort,
       CASE
           WHEN d.status = 'ACCEPTED' THEN 1.0
           WHEN COALESCE(SUM(cp.effort), 0) = 0 THEN 0.0
           ELSE SUM(cp.progress * cp.effort) / SUM(cp.effort)
       END                                     AS progress,
       COALESCE(SUM(cp.spent_hours), 0)        AS spent_hours,
       COUNT(cp.component_id)                  AS components
FROM deliverables d
LEFT JOIN components c ON c.deliverable_id = d.id
LEFT JOIN component_progress cp ON cp.component_id = c.id
GROUP BY d.id, d.status;

-- Proyecto: ponderado por el esfuerzo de sus entregables.
CREATE VIEW project_progress AS
SELECT p.id                                    AS project_id,
       COALESCE(SUM(dp.effort), 0)             AS effort,
       CASE WHEN COALESCE(SUM(dp.effort), 0) = 0 THEN 0.0
            ELSE SUM(dp.progress * dp.effort) / SUM(dp.effort) END AS progress,
       COALESCE(SUM(dp.spent_hours), 0)        AS spent_hours,
       COUNT(dp.deliverable_id)                AS deliverables
FROM projects p
LEFT JOIN deliverables d ON d.project_id = p.id
LEFT JOIN deliverable_progress dp ON dp.deliverable_id = d.id
GROUP BY p.id;

-- ---------------------------------------------------------------------
-- Solo se asigna trabajo a quien participa en el proyecto
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_tasks_asignado_del_equipo()
RETURNS TRIGGER AS $$
DECLARE
    participa INTEGER;
BEGIN
    IF NEW.assignee_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT COUNT(*) INTO participa
    FROM project_memberships m
    WHERE m.project_id = NEW.project_id
      AND m.user_id = NEW.assignee_id
      AND m.project_role = 'TEAM_MEMBER'
      AND m.status = 'ACTIVE';

    IF participa = 0 THEN
        RAISE EXCEPTION 'WBS-09: solo se asignan tareas a miembros del equipo del proyecto'
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_tasks_asignado_del_equipo
    BEFORE INSERT OR UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION fn_tasks_asignado_del_equipo();
