-- =====================================================================
-- SLCP - Las credenciales pertenecen al proveedor, no a la funcion
--
-- Estaban guardadas en la configuracion de cada funcion, y cambiar de proveedor
-- retiraba la clave anterior. Eso tenia sentido mientras cada funcion usaba uno
-- solo, pero impide lo que hace falta ahora: guardar cuatro claves a la vez para
-- compararlas.
--
-- Una clave pertenece a quien la emitio, no a para que se use. Guardarla por
-- proveedor permite ademas configurar cinco funciones sin teclearla cinco veces.
-- =====================================================================

CREATE TABLE ai_credentials (
    project_id     UUID          NOT NULL,
    provider       VARCHAR(30)   NOT NULL,

    model          VARCHAR(120)  NOT NULL,
    base_url       VARCHAR(400)  NOT NULL,

    api_key_cipher TEXT          NOT NULL,
    key_hint       VARCHAR(12)   NOT NULL,

    updated_by     UUID          NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    lock_version   INTEGER       NOT NULL DEFAULT 0,

    PRIMARY KEY (project_id, provider),
    CONSTRAINT fk_cred_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_cred_updater FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_cred_provider CHECK (provider IN ('ANTHROPIC','OPENAI','DEEPSEEK','GOOGLE','COMPATIBLE'))
);

COMMENT ON TABLE ai_credentials IS
    'Una credencial por proveedor y proyecto. Varias pueden convivir: es lo que permite compararlas';

-- Se conserva lo ya guardado: una fila por proveedor distinto que hubiera.
INSERT INTO ai_credentials (project_id, provider, model, base_url, api_key_cipher, key_hint,
                            updated_by, updated_at)
SELECT DISTINCT ON (project_id, provider)
       project_id, provider, model, base_url, api_key_cipher, key_hint, updated_by, updated_at
FROM ai_settings
WHERE api_key_cipher IS NOT NULL
ORDER BY project_id, provider, updated_at DESC;

-- La configuracion de cada funcion pasa a decir solo que proveedor usa.
ALTER TABLE ai_settings DROP CONSTRAINT IF EXISTS ck_ai_enabled;

ALTER TABLE ai_settings
    DROP COLUMN api_key_cipher,
    DROP COLUMN key_hint,
    DROP COLUMN model,
    DROP COLUMN base_url;

-- No puede activarse una funcion cuyo proveedor no tiene credencial: quedaria
-- activa de nombre y fallaria en cada uso.
CREATE OR REPLACE FUNCTION fn_ai_exige_credencial()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.enabled AND NOT EXISTS (
        SELECT 1 FROM ai_credentials c
        WHERE c.project_id = NEW.project_id AND c.provider = NEW.provider) THEN

        RAISE EXCEPTION 'API-02: no puede activarse % sin credencial de %',
            NEW.feature, NEW.provider USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_ai_exige_credencial
    BEFORE INSERT OR UPDATE ON ai_settings
    FOR EACH ROW EXECUTE FUNCTION fn_ai_exige_credencial();
