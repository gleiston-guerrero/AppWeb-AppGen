# Primera pantalla: instrucciones

> **No verificado en el entorno donde se escribió.** No hay acceso al registro de paquetes ni forma de ejecutar Angular allí. Su primera ejecución es la primera verificación.

## 1. Extraer

Extraiga el contenido de `web/` **dentro de** `D:\Repositorios\AppWeb-AppGen\web`, sustituyendo los archivos que coincidan.

Archivos que **sustituye**: `src/app/app.config.ts`, `src/app/app.routes.ts`, `src/app/app.html`, `src/app/app.spec.ts`.
Archivos **nuevos**: todo lo de `src/app/platform-info/`, más `proxy.conf.json`.

`app.spec.ts` se sustituye porque la prueba generada comprobaba el texto `Hello, web`, que ya no existe.

## 2. Activar el proxy

Edite `web/package.json` y cambie la línea del script `start`:

```json
"start": "ng serve --proxy-config proxy.conf.json",
```

Sin esto el navegador intentaría llamar a `localhost:4200/api/...`, que no existe, y fallaría. El proxy redirige esas rutas al servicio en el 8081, y de paso evita el problema de CORS.

## 3. Ejecutar

Necesita **dos ventanas de PowerShell**.

Ventana 1, el servicio:

```powershell
cd D:\Repositorios\AppWeb-AppGen\service
& "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run
```

Ventana 2, la interfaz (deténgala antes con Ctrl+C si estaba corriendo, para que tome el proxy):

```powershell
cd D:\Repositorios\AppWeb-AppGen\web
npm start
```

Abra **http://localhost:4200**

## 4. Ejecutar las pruebas

```powershell
cd D:\Repositorios\AppWeb-AppGen\web
npm test
```

Se esperan 7: una de `App`, tres del servicio y tres de la pantalla. No necesitan que el servicio esté en marcha; la respuesta se simula.

## Qué debe ver

El nombre de la plataforma, su propósito, cuál es su materia prima, la lista de lo que produce, con qué está construida, la autoría con la UTEQ y la licencia MIT con enlace al repositorio.

**Si el servicio está apagado**, verá un aviso rojo explicando cómo arrancarlo. Es deliberado: es el caso que más veces se encontrará mientras desarrolla, y una pantalla en blanco no dice nada.

## Requisitos que realiza

| Requisito | Dónde |
|---|---|
| FUN-01 — información pública sin sesión: construcción, capacidades, insumo | `platform-info-page.html` |
| FUN-02 — autoría, institución y licencia | pie de `platform-info-page.html` |
| VER-01 — prueba antes que implementación | los dos `.spec.ts` |

## Si falla

| Síntoma | Causa probable |
|---|---|
| `Cannot find module '@angular/common/http/testing'` | Nada que instalar: viene con Angular. Revise que la ruta del import esté completa |
| La pantalla muestra el aviso rojo | El servicio no está en marcha, o `npm start` no tomó el proxy. Reinicie `npm start` tras editar `package.json` |
| `404` en la consola del navegador al pedir `/api/v1/platform-info` | Falta el `--proxy-config` en el script `start` |
| Error de compilación en un `.spec.ts` | Cambio de API en Angular 22. Péguemelo y lo corrijo |
