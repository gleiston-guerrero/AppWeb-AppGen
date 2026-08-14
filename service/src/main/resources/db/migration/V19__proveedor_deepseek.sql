-- =====================================================================
-- SLCP - DeepSeek como proveedor de IA generativa
--
-- La restriccion enumeraba los proveedores admitidos, de modo que anadir uno al
-- codigo sin tocarla haria que guardar la configuracion fallara contra la base:
-- el codigo lo ofreceria y la base lo rechazaria.
-- =====================================================================

ALTER TABLE ai_settings DROP CONSTRAINT ck_ai_provider;

ALTER TABLE ai_settings
    ADD CONSTRAINT ck_ai_provider
        CHECK (provider IN ('ANTHROPIC','OPENAI','DEEPSEEK','GOOGLE','COMPATIBLE'));
