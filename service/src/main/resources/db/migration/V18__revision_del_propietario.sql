-- =====================================================================
-- SLCP - Revision del propietario del producto sobre lo generado
--
-- Las pruebas y los diagramas los aprueba el equipo de desarrollo: son
-- artefactos tecnicos y quien los juzga es quien va a ejecutarlos.
--
-- El propietario del producto no aprueba, pero si puede dar por revisado lo
-- generado, antes o despues de que el equipo lo apruebe. Son dos actos
-- distintos: uno dice "esto sirve para probar el sistema" y el otro, "he visto
-- lo que se va a probar". Guardarlos en la misma columna obligaria a elegir cual
-- de los dos se pierde.
-- =====================================================================

ALTER TABLE generated_artifacts
    ADD COLUMN owner_reviewed_by UUID,
    ADD COLUMN owner_reviewed_at TIMESTAMPTZ;

ALTER TABLE generated_artifacts
    ADD CONSTRAINT fk_artifacts_owner_reviewer
        FOREIGN KEY (owner_reviewed_by) REFERENCES users (id);

-- Si consta revisado, consta quien y cuando: una revision sin autor no responde
-- a la pregunta que justifica registrarla.
ALTER TABLE generated_artifacts
    ADD CONSTRAINT ck_artifacts_owner_review
        CHECK ((owner_reviewed_by IS NULL) = (owner_reviewed_at IS NULL));

COMMENT ON COLUMN generated_artifacts.owner_reviewed_by IS
    'Propietario del producto que lo dio por revisado. No es una aprobacion';
