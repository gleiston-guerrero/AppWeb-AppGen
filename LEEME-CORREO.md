# Envío de la invitación por correo

## Antes de nada: tres avisos sobre la credencial

**1. La contraseña que me pasó está ahora en el historial de una conversación.** Cámbiela.

**2. Esa contraseña no va a funcionar.** Google retiró el acceso con contraseña de cuenta para SMTP. Hace falta una **contraseña de aplicación** de 16 caracteres:

- Active la verificación en dos pasos en la cuenta `tddiagen@gmail.com`
- Vaya a la configuración de seguridad de Google → *Contraseñas de aplicaciones*
- Genere una para «Correo». Google la muestra **una sola vez**
- Úsela sin espacios

**3. El repositorio es público.** La credencial **no** figura en ningún archivo y no debe figurar: lo que entra en el historial de Git no se puede retirar después. Va solo por variables de entorno.

## Configurar

```powershell
$env:SLCP_MAIL_USERNAME = "tddiagen@gmail.com"
$env:SLCP_MAIL_PASSWORD = "<contrasena de aplicacion, 16 caracteres, sin espacios>"

cd C:\Repositorios\AppWeb-AppGen\service
mvn clean spring-boot:run
```

Para que persista entre sesiones de PowerShell:

```powershell
[Environment]::SetEnvironmentVariable("SLCP_MAIL_USERNAME","tddiagen@gmail.com","User")
[Environment]::SetEnvironmentVariable("SLCP_MAIL_PASSWORD","<contrasena de aplicacion>","User")
```

**Sin esas variables el envío queda desactivado** y la plataforma sigue funcionando como hasta ahora: devuelve el enlace a quien invita.

## Cómo se comporta

| Situación | Qué ve quien invita |
|---|---|
| Correo enviado | «Correo enviado a …». **El enlace no se muestra** |
| Envío falló | El enlace, con el motivo del fallo, para hacerlo llegar por otro medio |
| Envío desactivado | El enlace, como hasta ahora |

Que el enlace desaparezca cuando el correo sale **no es un detalle estético**: es lo que hace real la verificación del correo de INV-03. Si quien invita puede ver el enlace, puede usarlo él mismo y crear una cuenta a nombre de una dirección ajena.

## Probar

Invite a una dirección real suya desde *Ver equipo*. Debe llegarle el correo y la interfaz decir «Correo enviado a …» **sin mostrar enlace**.

Compruebe el rastro:

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = "slcp_dev_only"
& $psql -U slcp -d slcp -c "SELECT event_type, actor_label FROM event_records WHERE event_type LIKE 'INVITATION%' ORDER BY occurred_at DESC LIMIT 5;"
```

`INVITATION_SENT` si salió, `INVITATION_SENT_UNDELIVERED` si no. La distinción importa: si mañana alguien dice que nunca recibió nada, el registro lo dirá.

## Si falla el envío

| Mensaje | Causa |
|---|---|
| `Username and Password not accepted` | Está usando la contraseña de la cuenta y no una de aplicación |
| `Application-specific password required` | Lo mismo, dicho por Google con más claridad |
| `Connection timed out` | Su red bloquea el puerto 587. Algunas redes institucionales lo hacen |
| `must issue a STARTTLS command first` | Puerto equivocado. 587 con STARTTLS o 465 con SSL |

La invitación **se crea igualmente** aunque el correo falle. Una caída del servidor de correo no debe impedir incorporar a nadie.

## Límite que conviene conocer

Una cuenta de Gmail personal admite unos cientos de envíos al día. Suficiente para desarrollo y para un uso institucional modesto; insuficiente si esto crece. Cuando llegue ese momento, la salida es un dominio propio con servicio de envío, no más cuentas de Gmail.
