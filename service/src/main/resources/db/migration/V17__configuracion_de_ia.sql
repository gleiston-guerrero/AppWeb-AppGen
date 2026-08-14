-- =====================================================================
-- SLCP - Configuracion del servicio de IA generativa
--
-- Cada proyecto elige su proveedor y aporta su clave: quien paga la generacion
-- es el proyecto, y una clave comun en la plataforma haria que el consumo de uno
-- lo pagara otro sin saberlo.
--
-- La clave se guarda cifrada y nunca se devuelve. Lo que viaja al navegador es
-- una pista --- los cuatro ultimos caracteres --- que basta para reconocer cual
-- esta puesta y no sirve para usarla.
-- =====================================================================

CREATE TABLE ai_settings (
    project_id    UUID          PRIMARY KEY,

    provider      VARCHAR(30)   NOT NULL,
    model         VARCHAR(120)  NOT NULL,
    base_url      VARCHAR(400)  NOT NULL,

    -- Cifrada con la clave maestra de la instalacion. Sin ella no se descifra,
    -- de modo que una copia de la base robada no entrega las credenciales.
    api_key_cipher TEXT,
    -- Ultimos caracteres, para que quien la puso reconozca cual es.
    key_hint      VARCHAR(12),

    enabled       BOOLEAN       NOT NULL DEFAULT FALSE,

    updated_by    UUID          NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    lock_version  INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT fk_ai_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_updater FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_ai_provider CHECK (provider IN ('ANTHROPIC','OPENAI','GOOGLE','COMPATIBLE')),
    -- No puede quedar activada sin clave: lo estaria de nombre y fallaria en cada
    -- generacion, y el fallo se leeria como que el modelo no sirve.
    CONSTRAINT ck_ai_enabled  CHECK (NOT enabled OR api_key_cipher IS NOT NULL)
);

COMMENT ON COLUMN ai_settings.api_key_cipher IS
    'Clave cifrada con AES-GCM. Nunca se devuelve por la API';
