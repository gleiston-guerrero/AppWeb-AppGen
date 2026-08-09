# Todas las formas de incorporar a alguien al equipo

> Verificado aquí: **6 migraciones** contra PostgreSQL 17 (incluida V5 corregida), **57 pruebas Java** en verde, y toda la interfaz con tipos estrictos y `strictTemplates`. El servicio Spring no se compila aquí: `mvn test` es su verificación.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-invitaciones.zip" -DestinationPath . -Force
Remove-Item LEEME-INVITACIONES.md
cd service
mvn clean spring-boot:run
```

## Un solo formulario, tres caminos

Quien invita indica siempre lo mismo: **un correo o usuario, y un rol**. El servicio decide el camino según a quién se incorpora. No se le pide elegir el camino porque no le corresponde saberlo.

| Situación | Qué ocurre | Dónde se resuelve |
|---|---|---|
| **Ya tiene cuenta** | Se le propone. Le aparece en su espacio de trabajo y decide | El invitado acepta o rechaza |
| **No tiene cuenta** | Se genera un enlace de un solo uso | El invitado completa su registro y queda dentro |
| **Ya tiene ese rol** | Se rechaza con explicación | — |

## Las cuatro pantallas

**1. Facilitador — incorporar.** Dentro de *Ver equipo*, en los proyectos donde lo es. Al invitar aparece el resultado: qué camino tomó y, si procede, el enlace con botón de copiar.

**2. Facilitador — invitaciones sin responder.** Lista con el estado de cada una y botón para **retirar**: el enlace deja de servir de inmediato.

**3. Invitado sin cuenta — el enlace.** En `/invitacion/<token>`. Muestra el proyecto, quién invita y **su rol, que no puede modificar**. Debajo, el formulario para elegir usuario y contraseña. El correo viene fijado por la invitación.

**4. Invitado con cuenta — su espacio de trabajo.** Arriba del todo, «Le han invitado», con Aceptar y Rechazar.

## Probar los tres caminos

**Camino A — persona sin cuenta:**
1. Entre como facilitador → *Ver equipo* → invite a `nuevo@uteq.edu.ec` como *Miembro del equipo*
2. Copie el enlace y ábralo en una **ventana privada**
3. Complete el registro. La cuenta nace operativa: no pasa por el administrador
4. Entre con ella: verá el proyecto y su rol, sin poder invitar a nadie

**Camino B — persona con cuenta:** invite al correo de una cuenta existente. Entre con ella y verá «Le han invitado» arriba, con Aceptar y Rechazar.

**Camino C — se rechaza:** invite a alguien que ya sea *Miembro del equipo* como *Propietario del producto*. Debe negarse citando ROL-06.

## Comprobaciones que le pido

**El enlace es de un solo uso.** Complete un registro y vuelva a abrir el mismo enlace: debe decir que ya se usó.

**Retirar surte efecto de inmediato.** Invite, copie el enlace, retire la invitación, y abra el enlace: debe decir que fue retirada.

**No se revela nada antes de tiempo.** El enlace solo muestra nombre del proyecto, quién invita y el rol. Ni requisitos, ni artefactos, ni quiénes forman el equipo (INV-03).

**Y el rastro:**

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = "slcp_dev_only"
& $psql -U slcp -d slcp -c "SELECT event_type, actor_label FROM event_records WHERE event_type LIKE 'INVITATION%' ORDER BY occurred_at DESC;"
```

## Una debilidad conocida, mientras no haya envío de correo

**El enlace se le muestra a quien invita**, para que pueda hacerlo llegar. Eso significa que quien invita podría usarlo él mismo y crear una cuenta a nombre de una dirección ajena, con lo que la verificación del correo (INV-03) queda debilitada.

Es aceptable en desarrollo y **debe dejar de serlo en cuanto la plataforma envíe correo**: entonces el enlace no debe devolverse nunca a quien invita. Está anotado en el código y aquí para que no se pierda.
