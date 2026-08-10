# Recuperación de acceso

> Verificado aquí: **8 migraciones** contra PostgreSQL 17, **110 pruebas Java** en verde, interfaz con tipos estrictos y `strictTemplates`.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-recuperacion.zip" -DestinationPath . -Force
Remove-Item LEEME-RECUPERACION.md
$env:SLCP_MAIL_USERNAME = "tddiagen@gmail.com"
$env:SLCP_MAIL_PASSWORD = "<contrasena de aplicacion>"
cd service
mvn clean spring-boot:run
```

**El correo tiene que estar configurado.** Sin él, la recuperación falla con un mensaje explícito, y es deliberado: ver más abajo.

## Los tres caminos

| Situación | Dónde |
|---|---|
| Olvidó la contraseña | **Entrar** → *¿Olvidó su contraseña?* → `/recuperar` |
| Recibió el enlace | `/recuperar/<token>` |
| Está dentro y quiere cambiarla | `PUT /api/v1/auth/sessions/current/password` |

## Probar

1. Vaya a **http://localhost:4200/entrar** → *¿Olvidó su contraseña?*
2. Indique su usuario o correo → llega el enlace, con plazo de **30 minutos**
3. Ábralo, fije una contraseña de 15 caracteres o más
4. Compruebe que **su sesión anterior dejó de servir**

## Cuatro decisiones que conviene conocer

**El enlace no se devuelve nunca a quien lo pide, ni aunque falle el correo.** Es la diferencia con las invitaciones: allí quien invita está autenticado y autorizado, así que devolverle el enlace cuando el correo falla es aceptable. Aquí quien pide es anónimo — devolvérselo permitiría pedir la recuperación de cualquier cuenta y recibir la llave. Si el correo no sale, la operación fracasa y lo dice.

**Pedir un enlace nuevo invalida el anterior.** De otro modo quedarían varios válidos a la vez y bastaría con que se filtrase cualquiera.

**Cambiar la contraseña cierra todas las sesiones abiertas.** Quien recupera su acceso suele hacerlo porque sospecha que alguien más lo tiene. Si las sesiones sobrevivieran, el cambio no serviría de nada: esa persona seguiría dentro hasta que caducara su token.

**Treinta minutos, no siete días.** Una invitación puede tardar días en atenderse; una recuperación se usa en el momento. Cada minuto de más es tiempo en que un enlace filtrado sigue sirviendo.

## Cambiar la contraseña estando dentro

Exige la actual aunque haya sesión: una sesión abierta y desatendida no debe bastar para apropiarse de la cuenta. Resuelve además el aviso pendiente del administrador, cuya contraseña inicial figura en el repositorio.

```powershell
$cambio = @{ currentPassword="cambiar esta contrasena ya"; newPassword="una frase larga y propia" } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri http://localhost:8081/api/v1/auth/sessions/current/password -ContentType "application/json" -Body $cambio -WebSession $s
```

## El rastro

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = "slcp_dev_only"
& $psql -U slcp -d slcp -c "SELECT event_type, actor_label, payload FROM event_records WHERE event_type LIKE 'PASSWORD%' ORDER BY occurred_at DESC LIMIT 10;"
```

Se registran también las solicitudes fallidas: `PASSWORD_RESET_UNKNOWN` con el identificador probado. Una ráfaga de esas contra identificadores distintos desde el mismo origen es exactamente lo que hay que poder ver después.

## Lo que aún falta en la interfaz

La pantalla de cambio con sesión iniciada no existe todavía: el punto de acceso sí, pero hay que llamarlo por consola. Es lo siguiente, junto con llevar al administrador a esa pantalla cuando entra con la contraseña inicial.
