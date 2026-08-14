-- =====================================================================
-- SLCP - La decision del equipo prevalece
--
-- La comprobacion informa; no decide. Impedir la aceptacion de lo que tiene
-- reparos convierte una advertencia en un veto, y pone a la plataforma --- o al
-- modelo que la asiste --- por encima de quien responde del sistema.
--
-- Lo que si se hace es dejar constancia: si se acepto con reparos pendientes,
-- consta cuantos habia. Aceptar sobre un aviso es legitimo; que no se sepa que
-- lo habia, no.
-- =====================================================================

ALTER TABLE generated_artifacts
    ADD COLUMN accepted_with_gaps BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN generated_artifacts.accepted_with_gaps IS
    'Se acepto teniendo huecos o reparos pendientes. La decision del equipo prevalece';

ALTER TABLE specifications
    ADD COLUMN accepted_with_issues INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN specifications.accepted_with_issues IS
    'Cuantos reparos habia al aceptarla. Cero significa que no habia ninguno';

-- Si consta que se acepto con reparos, ha de estar aceptada: de otro modo el
-- dato hablaria de una decision que nadie tomo.
ALTER TABLE specifications
    ADD CONSTRAINT ck_spec_gaps
        CHECK (accepted_with_issues = 0 OR status = 'ACCEPTED');
