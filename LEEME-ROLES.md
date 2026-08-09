# Interfaces por rol

> El servicio Java **no se compila aquí**. El SQL (5 migraciones, comprobadas contra PostgreSQL 17), el dominio Java (**43 pruebas en verde**) y toda la interfaz (tipos estrictos y `strictTemplates`) sí están verificados.

## Instalar

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-roles.zip" -DestinationPath . -Force
Remove-Item LEEME-ROLES.md
cd service
mvn clean spring-boot:run
```

Flyway aplicará `V5`. En otra ventana: `cd web`, `npm start`.

## Los dos niveles de rol, que no son lo mismo

| Nivel | Qué expresa | Valores |
|---|---|---|
| **Plataforma** | Qué puede hacerse **sin** proyecto | `ADMINISTRATOR`, `FACILITATOR`, `MEMBER` |
| **Proyecto** | Qué puede hacerse **dentro de** uno | `PROJECT_FACILITATOR`, `TEAM_MEMBER`, `PRODUCT_OWNER` |

Una misma persona puede ser facilitadora en un proyecto y propietaria en otro. Por eso el rol se resuelve siempre respecto del proyecto (ROL-01), y por eso **no viaja dentro del token** (SEC-04).

## Qué ve cada quien

| Rol | Al entrar | Puede |
|---|---|---|
| **Visitante** | Portada | Conocer la plataforma, solicitar registro, entrar |
| **Administrador** | `/administracion` | Aprobar y rechazar solicitudes. **Nunca** el contenido de un proyecto |
| **Facilitador** | `/trabajo` | Crear proyectos, ver los suyos, incorporar equipo |
| **Miembro / Propietario** | `/trabajo` | Sus proyectos y su rol en cada uno |

Es **una sola pantalla de trabajo**, no una por rol. El motivo: alguien puede ser facilitador en un proyecto y propietario en otro; separar por rol le obligaría a cambiar de pantalla para ver su propio trabajo.

## Probar de punta a punta

1. Entre como su cuenta aprobada. Irá a **Mi trabajo**
2. Cree un proyecto. Queda como su facilitador
3. Pulse **Ver equipo**: aparece usted como Facilitador
4. Incorpore a alguien indicando su usuario o correo y un rol
5. Entre con esa otra cuenta: verá el proyecto y su rol, **sin** poder incorporar a nadie

## Comprobar que el control es real

**ROL-06 — quien produce no aprueba.** Incorpore a alguien como *Miembro del equipo* e intente añadirlo además como *Propietario*. Debe rechazarlo. Y no solo en la aplicación:

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = "slcp_dev_only"
& $psql -U slcp -d slcp -c "INSERT INTO project_memberships VALUES (gen_random_uuid(), (SELECT id FROM projects LIMIT 1), (SELECT user_id FROM project_memberships WHERE project_role='TEAM_MEMBER' LIMIT 1), 'PRODUCT_OWNER','ACTIVE',now());"
```

Debe fallar con `uq_memberships_segregacion`. Un control que se sortea con una consulta suelta no es un control.

**Alcance por membresía.** Con la sesión de alguien que no participa:

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/projects/PRJ-0001-v1/team -WebSession $s
```

Debe responder «No existe ese proyecto» — el mismo mensaje que si de verdad no existiera. Quien no participa no debe poder averiguar qué proyectos hay.

## Lo que aún no existe

La incorporación por invitación a quien **no tiene cuenta**. Hoy hay que incorporar a personas ya registradas; si indica un correo desconocido, el servicio lo dice explícitamente. El flujo completo de invitación está especificado en SLCP-ADR-0005 §3 y es el incremento siguiente.
