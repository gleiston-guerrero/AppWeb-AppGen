-- =====================================================================
-- SLCP - Ensayo comparativo de proveedores
--
-- Que proveedor sirve mejor a cada funcion no puede saberse de antemano: las
-- comparativas publicadas miden con conjuntos de proposito general, no con
-- requisitos de un dominio concreto en castellano.
--
-- Lo que si puede hacerse es medirlo sobre los requisitos del propio proyecto,
-- con las medidas que la plataforma ya sabe calcular: cuantos huecos deja cada
-- uno, cuantas cifras se inventa, cuantos reparos trae, y --- la que importa ---
-- cuantas propuestas acepta el equipo sin tocarlas.
--
-- Se guarda porque un ensayo que no queda registrado no es evidencia: no puede
-- repetirse, ni citarse, ni compararse con el siguiente.
-- =====================================================================

CREATE TABLE benchmark_runs (
    id            UUID          PRIMARY KEY,
    project_id    UUID          NOT NULL,

    feature       VARCHAR(40)   NOT NULL,
    -- Requisitos sobre los que se ensayo, para poder repetirlo igual.
    requirements  TEXT          NOT NULL,
    subkind       VARCHAR(40),

    run_by        UUID          NOT NULL,
    run_at        TIMESTAMPTZ   NOT NULL,
    notes         TEXT,

    CONSTRAINT fk_br_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_br_runner  FOREIGN KEY (run_by) REFERENCES users (id),
    CONSTRAINT ck_br_feature CHECK (feature IN ('GENERATE_TESTS','GENERATE_SPECS','GENERATE_DIAGRAMS'))
);

CREATE INDEX ix_br_project ON benchmark_runs (project_id, run_at DESC);

-- ---------------------------------------------------------------------
-- Lo que cada proveedor produjo, con sus medidas
-- ---------------------------------------------------------------------
CREATE TABLE benchmark_results (
    id             UUID          PRIMARY KEY,
    run_id         UUID          NOT NULL,

    provider       VARCHAR(30)   NOT NULL,
    model          VARCHAR(120)  NOT NULL,

    -- Cuanto produjo
    produced       INTEGER       NOT NULL DEFAULT 0,
    -- Cuantas quedaron listas para ejecutar, sin huecos
    complete       INTEGER       NOT NULL DEFAULT 0,
    -- Cifras que invento y la salvaguarda sustituyo: mide cuanto se inventa
    invented       INTEGER       NOT NULL DEFAULT 0,
    -- Reparos del validador sobre lo producido
    issues         INTEGER       NOT NULL DEFAULT 0,
    -- Milisegundos que tardo
    elapsed_ms     INTEGER       NOT NULL DEFAULT 0,

    failed         BOOLEAN       NOT NULL DEFAULT FALSE,
    failure_reason TEXT,

    -- Lo generado, para poder leerlo y no solo contarlo
    sample         TEXT,

    CONSTRAINT fk_bres_run FOREIGN KEY (run_id) REFERENCES benchmark_runs (id) ON DELETE CASCADE,
    CONSTRAINT uq_bres     UNIQUE (run_id, provider, model),
    -- Si fallo, ha de decirse por que: un fallo sin motivo no informa de nada.
    CONSTRAINT ck_bres_fail CHECK (NOT failed OR failure_reason IS NOT NULL)
);

CREATE INDEX ix_bres_run ON benchmark_results (run_id);

COMMENT ON COLUMN benchmark_results.invented IS
    'Cifras que el modelo puso y no estaban en el requisito. Menos es mejor';
