# Administrador y aprobación de solicitudes

> El servicio Java **no ha podido compilarse aquí** (sin acceso al repositorio de artefactos de Maven). El SQL, el dominio Java y toda la interfaz **sí están verificados**.

## Instalación

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-administrador.zip" -DestinationPath . -Force
Remove-Item LEEME-ADMINISTRADOR.md
cd service
mvn clean spring-boot:run
```

Flyway aplicará `V4`, que añade el rol de plataforma y **crea la cuenta de administrador**.

## Credenciales iniciales

```
usuario:     administrador
contraseña:  cambiar esta contrasena ya
```

Están en el repositorio a propósito y por eso la cuenta nace con la marca de cambio obligatorio. La pantalla se lo advertirá en cuanto entre.

## Probar

1. Levante el servicio y, en otra ventana, `cd web` y `npm start`
2. Abra **http://localhost:4200/entrar**
3. Entre como `administrador`
4. Le llevará a **Administración**, con las solicitudes pendientes

Ahí puede **aprobar** —un clic— o **rechazar**, que exige motivo: el botón permanece deshabilitado mientras no lo escriba, igual que el servicio lo rechaza sin él.

## Comprobar que quedó rastro

```powershell
$env:PGPASSWORD = "slcp_dev_only"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
& $psql -U slcp -d slcp -c "SELECT event_type, actor_label, payload FROM event_records ORDER BY occurred_at DESC LIMIT 5;"
```

Debe aparecer `REGISTRATION_APPROVED` o `REGISTRATION_REJECTED` **con el nombre de quien decidió**. Eso es lo que el `UPDATE` a mano no dejaba.

## Comprobar que el control es real, no visual

Con sesión de una cuenta que **no** sea administrador:

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/administration/registrations/pending -WebSession $s
```

Debe responder 403 aunque la opción no aparezca en el menú. SEC-05: ocultar no es autorizar.

## Sobre las tildes

Si `psql` muestra `Cicer¾n` en lugar de `Cicerón`, compruebe si el dato está bien o mal:

```powershell
& $psql -U slcp -d slcp -c "SELECT full_name, length(full_name) AS caracteres, octet_length(full_name) AS bytes FROM users WHERE username='gguerrero1971';"
```

Si `bytes` > `caracteres`, el dato está correcto y solo se ve mal en consola. Se corrige con:

```powershell
chcp 65001
$env:PGCLIENTENCODING = "UTF8"
```

Si son iguales, el dato sí se corrompió y hay que revisar la conexión.
