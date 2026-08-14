-- =====================================================================
-- SLCP - Un proveedor de IA por cada funcion
--
-- Las funciones no piden lo mismo. Validar requisitos son peticiones cortas y
-- frecuentes, donde interesa un modelo barato; generar casos de uso son pocas
-- peticiones largas, donde interesa el mejor. Obligar a un solo proveedor para
-- todo hace pagar el modelo caro en lo que no lo necesita, o conformarse con el
-- barato en lo que si.
--
-- Ademas permite lo que de otro modo no cabria: probar un proveedor en una
-- funcion sin tocar las demas.
-- =====================================================================

-- La clave deja de ser el proyecto y pasa a ser proyecto y funcion.
ALTER TABLE ai_settings DROP CONSTRAINT ai_settings_pkey;

ALTER TABLE ai_settings
    ADD COLUMN feature VARCHAR(40) NOT NULL DEFAULT 'GENERATE_TESTS';

-- Lo que hubiera configurado sirve para todas: quien lo puso lo puso para lo
-- unico que habia, y perderlo obligaria a teclear la clave otra vez.
INSERT INTO ai_settings (project_id, feature, provider, model, base_url, api_key_cipher,
                         key_hint, enabled, updated_by, updated_at)
SELECT s.project_id, f.feature, s.provider, s.model, s.base_url, s.api_key_cipher,
       s.key_hint, s.enabled, s.updated_by, s.updated_at
FROM ai_settings s
CROSS JOIN (VALUES ('VALIDATE_REQUIREMENTS'), ('GENERATE_SPECS'), ('GENERATE_DIAGRAMS'),
                   ('GENERATE_CODE')) AS f(feature)
WHERE s.feature = 'GENERATE_TESTS';

ALTER TABLE ai_settings ADD PRIMARY KEY (project_id, feature);

ALTER TABLE ai_settings
    ADD CONSTRAINT ck_ai_feature CHECK (feature IN (
        'VALIDATE_REQUIREMENTS',
        'GENERATE_TESTS',
        'GENERATE_SPECS',
        'GENERATE_DIAGRAMS',
        'GENERATE_CODE'));

COMMENT ON COLUMN ai_settings.feature IS
    'Funcion a la que sirve esta configuracion. Cada una puede usar un proveedor distinto';
