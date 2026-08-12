-- =====================================================================
-- SLCP - Constancia de la revision previa (RQM-05)
--
-- Quien reviso un requisito no puede aprobarlo. La comprobacion recae sobre la
-- persona y no sobre el rol: en un equipo pequeno alguien puede ser a la vez
-- facilitador y propietario, y es entonces cuando la doble etapa corre peligro
-- de convertirse en un tramite de una sola firma.
-- =====================================================================

ALTER TABLE requirements ADD COLUMN reviewed_by UUID;

ALTER TABLE requirements
    ADD CONSTRAINT fk_requirements_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id);
