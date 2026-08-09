# `service` — Servicio SLCP

Servicio HTTP de la plataforma.

> ## AVISO: no verificado en el entorno donde se escribió
>
> El entorno donde se redactó no tiene acceso al repositorio de artefactos de Maven ni motor de base de datos. **Su primera ejecución es la primera verificación.** Si falla, pegue el error completo.

## Estado

| Función | Estado | Requisitos |
|---|---|---|
| Información pública de la plataforma | Verificado por usted | FUN-01, FUN-02 |
| Persistencia con PostgreSQL y Flyway | **Nuevo, sin verificar** | TRC-03, TRC-04, TRC-24 |
| Solicitud de registro | **Nuevo, sin verificar** | FUN-15 |
| Autenticación y sesión | No iniciado | FUN-03 a FUN-06 |
| Aprobación de facilitadores | No iniciado | FUN-16 |

## Requisitos previos

| Herramienta | Versión | Comprobar |
|---|---|---|
| JDK | 21 o superior | `java -version` |
| Maven | 3.9 o superior | `mvn -version` |
| Docker Desktop o Podman | reciente | `docker --version` |

## 1. Levantar la base de datos

Desde la raíz del repositorio:

```powershell
docker compose -f infra/docker-compose.yml up -d
docker ps
```

Debe aparecer un contenedor `slcp-postgres` en estado `healthy` al cabo de unos segundos.

### Si no tiene Docker

Instale PostgreSQL 17 de forma nativa y cree la base y el usuario:

```sql
CREATE DATABASE slcp;
CREATE USER slcp WITH PASSWORD 'slcp_dev_only';
GRANT ALL PRIVILEGES ON DATABASE slcp TO slcp;
```

La configuración por defecto apunta a `localhost:5432`, así que no hay que cambiar nada más.

## 2. Ejecutar las pruebas

```powershell
cd service
mvn test
```

Se esperan **16**: cuatro de información pública, cuatro de la máquina de estados, cuatro de la cuenta, cuatro de validación, y la de arranque del contexto.

**Las pruebas no necesitan la base de datos en marcha.** Usan H2 en memoria de forma provisional.

## 3. Arrancar

```powershell
mvn spring-boot:run
```

En el arranque verá a Flyway aplicar la migración `V1__esquema_inicial`. Si la base ya estaba migrada, dirá que no hay nada que hacer.

## 4. Probar el registro

```powershell
$cuerpo = @{
  username = "gguerrero"
  email    = "gguerrero@uteq.edu.ec"
  fullName = "Gleiston Guerrero-Ulloa"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/registrations `
  -ContentType "application/json" -Body $cuerpo
```

Debe devolver el identificador legible, el estado `PENDING_APPROVAL` y el aviso de que la cuenta no otorga capacidad alguna hasta ser aprobada.

Repita el mismo comando: la segunda vez debe fallar con conflicto, porque el usuario ya existe.

Compruebe que quedó registrado el evento:

```powershell
docker exec -it slcp-postgres psql -U slcp -d slcp -c "SELECT event_type, actor_label, occurred_at FROM event_records;"
```

## Decisiones que conviene conocer

**El servicio no arranca sin base de datos.** Es deliberado. Un servicio que arranca sin ella falla después, en la primera petición, y con un error mucho menos claro.

**Hibernate no toca el esquema.** `ddl-auto: validate` comprueba que el modelo y el esquema real coinciden y detiene el arranque si discrepan. Quien gobierna el esquema es Flyway, con migraciones versionadas.

**Los eventos no se pueden modificar.** La tabla `event_records` no tiene columna de actualización y la entidad carece de métodos que modifiquen, conforme a TRC-24.

**H2 en las pruebas es provisional.** VER-07 exige pruebas de integración contra PostgreSQL real en contenedor efímero. Se incorporan con Testcontainers en el incremento siguiente, y entonces H2 desaparece.

## Si falla

| Síntoma | Causa probable |
|---|---|
| `Connection to localhost:5432 refused` | PostgreSQL no está levantado |
| `Schema-validation: missing table` | Flyway no llegó a aplicar la migración. Revise el registro de arranque |
| `Flyway ... checksum mismatch` | Se modificó una migración ya aplicada. Nunca se editan: se añade una nueva |
| Error de compilación en un `.java` | Cambio de API en Spring Boot 4. Péguemelo y lo corrijo |

---

# Incremento de autenticación

## Qué se añadió

| Función | Requisitos |
|---|---|
| Inicio de sesión con nombre de usuario **o** correo | FUN-03 |
| Contraseña guardada como verificador, nunca en claro | FUN-04 |
| Política de contraseña: 15 caracteres mínimo, sin reglas de composición | FUN-05 |
| Registro de accesos y de intentos fallidos | FUN-06 |
| Token en cookie no legible por script | SEC-01 |
| Ninguna cookie de seguimiento | SEC-02 |
| Token corto con renovación revocable | SEC-03 |
| Sin roles dentro del token | SEC-04 |

## Rutas

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/v1/auth/sessions` | Inicia sesión. Devuelve cookies, no tokens en el cuerpo |
| `PUT` | `/api/v1/auth/sessions/current` | Renueva y **sustituye** el token de renovación |
| `GET` | `/api/v1/auth/sessions/current` | Quién está atendiéndose |
| `DELETE` | `/api/v1/auth/sessions/current` | Cierra sesión y **revoca en el servidor** |

Las rutas siguen la convención de recursos: sustantivos en plural, sin verbos, y el método HTTP expresa la acción. Renovar es sustituir la sesión vigente, de ahí `PUT`; cerrarla es borrar el elemento y no la colección, de ahí `/current` y no la raíz.

## Antes de arrancar

La contraseña ahora es obligatoria al registrarse. Si ya había creado cuentas con la versión anterior, no tienen verificador y no podrán acceder. La forma limpia de empezar:

```powershell
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
```

El `-v` borra el volumen, así que Flyway aplicará las tres migraciones desde cero.

## Probar de punta a punta

```powershell
# 1. Registrarse
$reg = @{ username="gguerrero"; email="gguerrero@uteq.edu.ec"; fullName="Gleiston Guerrero-Ulloa"; password="una frase larga de acceso" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/registrations -ContentType "application/json" -Body $reg

# 2. Intentar entrar: debe FALLAR, la cuenta está pendiente de aprobación
$login = @{ identifier="gguerrero"; password="una frase larga de acceso" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/sessions -ContentType "application/json" -Body $login

# 3. Activar la cuenta a mano (la pantalla del administrador es el paso siguiente)
docker exec -it slcp-postgres psql -U slcp -d slcp -c "UPDATE users SET status='ACTIVE';"

# 4. Entrar. -SessionVariable guarda las cookies
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/sessions -ContentType "application/json" -Body $login -SessionVariable s

# 5. Con la sesión abierta
Invoke-RestMethod -Uri http://localhost:8081/api/v1/auth/sessions/current -WebSession $s

# 6. Con el correo en lugar del usuario (FUN-03)
$login2 = @{ identifier="gguerrero@uteq.edu.ec"; password="una frase larga de acceso" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/sessions -ContentType "application/json" -Body $login2

# 7. Cerrar sesión
Invoke-RestMethod -Method Delete -Uri http://localhost:8081/api/v1/auth/sessions -WebSession $s
```

Compruebe el rastro que quedó:

```powershell
docker exec -it slcp-postgres psql -U slcp -d slcp -c "SELECT event_type, actor_label, occurred_at FROM event_records ORDER BY occurred_at;"
```

## Reglas que ahora impone la base de datos

Verificadas contra PostgreSQL 16 real antes de entregarse.

| Regla | Requisito | Cómo se impone |
|---|---|---|
| Un evento no se puede modificar ni borrar | TRC-24 | Disparador que rechaza `UPDATE` y `DELETE` |
| Los identificadores de acceso se crean y sincronizan solos | FUN-03 | Disparador sobre `users` |
| Nombre de usuario y correo comparten espacio de nombres | FUN-03 | Clave primaria de `login_identifiers` |
| El identificador interno y la fecha de creación son inmutables | TRC-03 | Disparador que rechaza el cambio |
| Un token revocado no vuelve a estar vigente | SEC-03 | Disparador sobre `refresh_tokens` |

**Lo que la base de datos NO impone**, y es deliberado: la máquina de estados de la cuenta. Es regla de negocio, vive en el dominio, y duplicarla en PL/pgSQL garantizaría que ambas versiones divergieran con el tiempo.

## Advertencia sobre la clave de firma

`application.yml` trae una clave de desarrollo. **En cualquier despliegue real debe venir de una variable de entorno:**

```powershell
$env:SLCP_SESSION_SECRET = "<cadena aleatoria de al menos 32 caracteres>"
```

Y `cookie-secure` debe pasar a `true` en cuanto haya HTTPS. Está en `false` solo porque el desarrollo local usa http.
