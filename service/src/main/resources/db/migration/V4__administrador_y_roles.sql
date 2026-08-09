-- =====================================================================
-- SLCP - Rol de plataforma y cuenta de administrador inicial
--
-- Resuelve un problema que no tiene salida dentro del propio flujo: nadie
-- puede aprobar al primer administrador, porque es quien aprueba. La cuenta
-- se siembra aqui, versionada y reproducible en cualquier despliegue, en lugar
-- de depender de que alguien recuerde crearla a mano.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Rol global de plataforma
--
-- Es el unico rol que no es por proyecto. ROL-01 establece que los demas se
-- resuelven respecto del proyecto sobre el que se actua; la administracion de
-- la plataforma no pertenece a ningun proyecto.
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN platform_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD CONSTRAINT ck_users_platform_role
    CHECK (platform_role IN ('USER', 'ADMINISTRATOR'));

-- ---------------------------------------------------------------------
-- 2. Cuenta de administrador inicial
--
-- El verificador corresponde a la contrasena 'cambiar esta contrasena ya',
-- derivada con bcrypt de coste 12. La marca de cambio obligatorio impide que
-- una contrasena conocida y publicada en el repositorio siga sirviendo mas
-- alla del primer acceso.
-- ---------------------------------------------------------------------
INSERT INTO users (
    id, readable_id, username, email, full_name,
    password_verifier, status, platform_role, must_change_password, created_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'USR-ADM-0001-v1',
    'administrador',
    'administrador@slcp.local',
    'Administrador de la plataforma',
    '$2a$12$mKdF4hWsFbR6mtyNIRMsCu1RQyTAnLKTn2rpvRycDFKop39ANeLNK',
    'ACTIVE',
    'ADMINISTRATOR',
    TRUE,
    now()
);

-- El disparador de V3 crea sus identificadores de acceso por si mismo.

-- ---------------------------------------------------------------------
-- 3. La siembra deja rastro, como cualquier otro acto
-- ---------------------------------------------------------------------
INSERT INTO event_records (
    id, occurred_at, event_type, subject_type, subject_id, actor_id, actor_label, payload
) VALUES (
    gen_random_uuid(), now(), 'PLATFORM_ADMIN_SEEDED', 'User',
    '00000000-0000-0000-0000-000000000001', NULL, 'migracion V4',
    'Cuenta de administrador inicial creada por migracion, con cambio de contrasena obligatorio'
);
