-- =====================================================================
-- SLCP - El facilitador tambien ejecuta
--
-- En esta plataforma el facilitador organiza el proyecto y ademas realiza el
-- trabajo del equipo de desarrollo. Se le asignan los dos roles de forma
-- expresa, y no se hace que uno implique al otro, porque los informes de
-- trabajo enumeran a quien ejecuta: un rol implicito dejaria fuera de esa lista
-- a quien si esta haciendo el trabajo.
--
-- Esta migracion completa los proyectos ya existentes. Sin ella, quien creo un
-- proyecto antes de esta regla seguiria sin poder redactar requisitos en el.
-- =====================================================================

INSERT INTO project_memberships (id, project_id, user_id, project_role, status, created_at)
SELECT gen_random_uuid(), f.project_id, f.user_id, 'TEAM_MEMBER', 'ACTIVE', now()
FROM project_memberships f
WHERE f.project_role = 'PROJECT_FACILITATOR'
  AND f.status = 'ACTIVE'
  -- Quien ya sea propietario del producto queda fuera: ROL-06 lo impide, y
  -- forzarlo aqui haria fracasar la migracion entera por un caso previsto.
  AND NOT EXISTS (
      SELECT 1 FROM project_memberships o
      WHERE o.project_id = f.project_id
        AND o.user_id = f.user_id
        AND o.project_role = 'PRODUCT_OWNER'
        AND o.status <> 'DECOMMISSIONED')
  AND NOT EXISTS (
      SELECT 1 FROM project_memberships t
      WHERE t.project_id = f.project_id
        AND t.user_id = f.user_id
        AND t.project_role = 'TEAM_MEMBER'
        AND t.status <> 'DECOMMISSIONED');
