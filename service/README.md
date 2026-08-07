# `service` — Servicio SLCP

Servicio HTTP de la plataforma. Primera versión: arranca sin ninguna dependencia externa.

> ## AVISO: no verificado en el entorno donde se escribió
>
> Este módulo **no ha podido compilarse ni ejecutarse** antes de entregarse. El entorno donde se redactó no tiene acceso al repositorio de artefactos de Maven (respuesta 403 comprobada).
>
> **Su primera ejecución es también su primera verificación.** Si falla, pegue el error completo y se corrige.
>
> El módulo `core/naming`, en cambio, sí está verificado: no tiene dependencias externas.

## Requisitos previos

| Herramienta | Versión | Comprobar |
|---|---|---|
| JDK | 21 o superior | `java -version` |
| Maven | 3.9 o superior | `mvn -version` |

Spring Boot 4.1 exige Java 17 como mínimo y admite hasta Java 26.

Si no tiene Maven:

```powershell
winget install Apache.Maven
```

Cierre y vuelva a abrir PowerShell después de instalarlo.

## Ejecutar las pruebas

```powershell
cd service
mvn test
```

Se esperan cinco pruebas: cuatro de `PlatformInfoTest` y la de arranque del contexto.

## Arrancar el servicio

```powershell
cd service
mvn spring-boot:run
```

Debe terminar mostrando algo como `Started SlcpApplication in X seconds`.

## Comprobar que responde

Con el servicio en marcha, en otra ventana de PowerShell:

```powershell
# Informacion publica de la plataforma (FUN-01, FUN-02)
Invoke-RestMethod http://localhost:8080/api/v1/platform-info | ConvertTo-Json -Depth 5

# Estado del servicio
Invoke-RestMethod http://localhost:8080/actuator/health
```

O en el navegador: <http://localhost:8080/api/v1/platform-info>

## Qué realiza este módulo

| Requisito | Dónde |
|---|---|
| FUN-01 — información pública sobre construcción, capacidades e insumo | `PlatformInfo`, `PlatformInfoController` |
| FUN-02 — autoría, institución y licencia | `PlatformInfo` |
| VER-01 — prueba antes que implementación | `PlatformInfoTest` escrita antes que el controlador |

## Qué NO incluye todavía, y por qué

Sin base de datos, sin autenticación y sin persistencia. Es deliberado: si el primer arranque fallara con JPA, Flyway y PostgreSQL de por medio, habría cuatro causas posibles en lugar de una. La persistencia entra en el incremento siguiente, una vez comprobado que el servicio arranca solo.

## Si falla

| Síntoma | Causa probable |
|---|---|
| `Could not resolve dependencies` o `Could not transfer artifact` | Sin conexión, o proxy corporativo. Compruebe `mvn -version` y su conexión |
| `invalid target release: 21` | Maven usa un JDK anterior. Compruebe `JAVA_HOME` |
| `Web server failed to start. Port 8080 was already in use` | Otro proceso ocupa el puerto. Cambie `server.port` en `application.yml` |
| Error de compilación en una prueba | Cambio de API entre versiones de Spring Boot. Péguemelo y lo corrijo |
