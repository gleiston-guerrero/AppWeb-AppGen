# Dónde va cada archivo

> **No verificado en el entorno donde se escribió.** Su primera ejecución es la primera verificación.

## Forma rápida

Extraiga el zip **en la raíz del repositorio**. La carpeta `web/` ya viene dentro, así que cada archivo cae en su sitio solo:

```powershell
cd D:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-web-portada.zip" -DestinationPath . -Force
```

## Qué hace cada archivo y dónde va

Rutas relativas a `D:\Repositorios\AppWeb-AppGen\web`.

### Sustituyen a archivos existentes

| Archivo | Ruta | Qué cambia |
|---|---|---|
| `styles.css` | `src\styles.css` | Sistema de diseño: color, tipografía, espaciado. Todo lo demás lo hereda de aquí |
| `index.html` | `src\index.html` | Idioma español, título y descripción |
| `app.ts` | `src\app\app.ts` | Armazón con cabecera de navegación |
| `app.html` | `src\app\app.html` | Cabecera, marca, navegación y área de contenido |
| `app.css` | `src\app\app.css` | Estilos de la cabecera |
| `app.routes.ts` | `src\app\app.routes.ts` | Dos rutas: portada y solicitud de registro |
| `app.config.ts` | `src\app\app.config.ts` | Añade el cliente HTTP |
| `app.spec.ts` | `src\app\app.spec.ts` | Pruebas del armazón |

### Nuevos

| Archivo | Ruta |
|---|---|
| `home-page.ts` · `.html` · `.css` · `.spec.ts` | `src\app\home\` |
| `registration-page.ts` · `.html` · `.css` | `src\app\registration\` |
| `registration-service.ts` · `registration.ts` · `registration-service.spec.ts` | `src\app\registration\` |
| `platform-info.ts` · `platform-info-service.ts` · `platform-info-service.spec.ts` | `src\app\platform-info\` |
| `proxy.conf.json` | raíz de `web\` |

### Hay que borrar

La pantalla anterior queda sustituida por la portada:

```powershell
cd D:\Repositorios\AppWeb-AppGen\web
Remove-Item src\app\platform-info\platform-info-page.ts
Remove-Item src\app\platform-info\platform-info-page.html
Remove-Item src\app\platform-info\platform-info-page.css
Remove-Item src\app\platform-info\platform-info-page.spec.ts
```

Si no los borra, la construcción sigue funcionando, pero quedan archivos que ninguna ruta usa y que el indicador de huérfanos de TRC-11 reportaría.

## Comprobar

```powershell
cd D:\Repositorios\AppWeb-AppGen\web
npm test
npm start
```

Se esperan **9 pruebas**: dos del armazón, tres de la portada, tres del servicio de información y dos del servicio de registro.

Con el servicio en marcha en el 8081, abra **http://localhost:4200**.

## Qué debe ver

**Portada.** Un encabezado que no dice lo que hace la plataforma sino que lo enseña: a la izquierda un requisito escrito en la sintaxis EARS que adopta SPC-01, a la derecha los cuatro artefactos que de él se derivan, con sus identificadores reales. Debajo, la materia prima, lo que produce y con qué está construida, todo traído del servicio. Al pie, la autoría con la UTEQ y la licencia MIT.

**Solicitud de registro.** Formulario de tres campos con validación en el propio navegador y mensajes que dicen qué corregir. Al enviarlo, la confirmación muestra el identificador asignado y el estado `PENDING_APPROVAL`.

**Iniciar sesión** aparece en la cabecera desactivado. Es deliberado: ocultarlo daría una idea equivocada del alcance, y fingir que funciona sería peor.

## Decisiones de diseño

**El azul es el mismo de los documentos de especificación.** El producto y su documentación se reconocen como una sola cosa.

**El verde significa verificado.** Es el único acento y aparece solo donde algo está comprobado o confirmado. En este proyecto todo gira alrededor de `RESULTADO: VERDE`.

**Todo lo estructural va en monoespaciada:** etiquetas, identificadores, estados. Es la voz de la casa, porque cada identificador del sistema tiene forma de código.

**Una sola animación**, la aparición escalonada de los artefactos derivados, y respeta `prefers-reduced-motion`.

## Si falla

| Síntoma | Causa probable |
|---|---|
| `Cannot find module './home/home-page'` | La carpeta `src\app\home` no se extrajo |
| La portada muestra el aviso de servicio caído | El servicio no está en marcha, o falta `--proxy-config` en el script `start` |
| Error al compilar un `.spec.ts` | Cambio de API en Angular 22. Péguemelo y lo corrijo |
| `NullInjectorError: No provider for HttpClient` | No se sustituyó `app.config.ts` |
