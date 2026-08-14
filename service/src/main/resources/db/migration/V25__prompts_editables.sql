-- =====================================================================
-- SLCP - Instrucciones editables, una por funcion
--
-- Estaban incrustadas en el codigo, de modo que solo podian cambiarse
-- recompilando. Quien conoce el dominio no es quien compila: si una instruccion
-- produce malos resultados en un proyecto concreto, ha de poder corregirse sin
-- esperar una version.
--
-- La instruccion es de la FUNCION, no del proveedor. Todas las APIs de una misma
-- funcion reciben exactamente la misma, y esa es la condicion que hace valida
-- una comparacion: con instrucciones distintas se compararian las instrucciones
-- y no los modelos.
-- =====================================================================

CREATE TABLE ai_prompts (
    project_id   UUID          NOT NULL,
    feature      VARCHAR(40)   NOT NULL,

    template     TEXT          NOT NULL,

    updated_by   UUID          NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    lock_version INTEGER       NOT NULL DEFAULT 0,

    PRIMARY KEY (project_id, feature),
    CONSTRAINT fk_prompt_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_prompt_updater FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_prompt_feature CHECK (feature IN (
        'VALIDATE_REQUIREMENTS','GENERATE_TESTS','GENERATE_SPECS','GENERATE_DIAGRAMS','GENERATE_CODE')),
    -- Una instruccion vacia dejaria al modelo sin nada que hacer y devolveria
    -- cualquier cosa. Para volver a la de fabrica se borra la fila.
    CONSTRAINT ck_prompt_no_vacio CHECK (length(trim(template)) >= 40)
);

COMMENT ON TABLE ai_prompts IS
    'Instruccion propia de un proyecto. Si no hay fila, rige la de fabrica del catalogo';

-- El ensayo pasa a cubrir todas las funciones, no solo dos.
ALTER TABLE benchmark_runs DROP CONSTRAINT ck_br_feature;

ALTER TABLE benchmark_runs
    ADD CONSTRAINT ck_br_feature CHECK (feature IN (
        'VALIDATE_REQUIREMENTS','GENERATE_TESTS','GENERATE_SPECS','GENERATE_DIAGRAMS','GENERATE_CODE'));

-- Que instruccion se uso en cada ensayo: sin ese dato, un ensayo de hace un mes
-- no puede compararse con el de hoy si alguien edito la instruccion entre medias.
ALTER TABLE benchmark_runs ADD COLUMN prompt_used TEXT;
