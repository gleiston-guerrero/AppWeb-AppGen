# Formatos de archivo admitidos

> Verificado aquí: **124 pruebas Java** en verde, los tres formatos comprobados contra sus propios ejemplos, y la ERS de MundiPets sigue dando 78 requisitos sin cambios.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-formatos.zip" -DestinationPath . -Force
Remove-Item LEEME-FORMATOS.md
cd service
mvn clean spring-boot:run
```

## Los tres formatos

| Formato | Extensiones | Para quién |
|---|---|---|
| **LaTeX con entorno de atributos** | `.tex` | Documentos con la plantilla IEEE de la asignatura |
| **Markdown con campos etiquetados** | `.md` `.markdown` `.txt` | Quien escribe en Markdown |
| **Texto plano con bloques separados** | `.txt` `.text` | Quien no usa ninguna de las dos |

Al elegir uno, la pantalla muestra su descripción, sus extensiones, cuántos campos reconoce, cuáles exige, y **un ejemplo de cómo debe verse el archivo**. El selector de archivo filtra por las extensiones de ese formato.

## Por qué un ejemplo y no una imagen

Pidió una imagen del formato. Preferí generar el ejemplo **desde el propio perfil instalado**, y creo que es mejor por tres razones.

Una captura se desactualiza en cuanto el formato cambia, y nadie se entera hasta que alguien prepara un archivo siguiendo una imagen que ya no corresponde. El ejemplo vive dentro del archivo de formato, así que no puede desviarse.

Además se puede copiar y pegar, que es lo que quien va a preparar un documento realmente necesita.

Y sobre todo: **hay una prueba que carga cada formato y extrae de su propio ejemplo**, exigiendo que salgan requisitos completos y sin etiquetas desconocidas. Si alguien cambia un formato y olvida su ejemplo, falla la compilación. Con una imagen eso no se puede comprobar.

## Añadir un formato

Copie un `.profile`, cambie los delimitadores y las etiquetas, escriba su ejemplo entre `example.begin` y `example.end`, y guárdelo en `service/src/main/resources/profiles/`. **Aparecerá solo en la lista**: se deriva de los perfiles instalados, no de una lista mantenida aparte.

## Ampliación del lector

Antes solo admitía bloques con marca de cierre explícita, como el entorno de LaTeX. Ahora admite además:

- `block.end = <NEXT>` — el bloque termina donde empieza el siguiente, para Markdown
- `block.end = <BLANK>` — termina en la primera línea vacía, para texto plano
- `name.pattern` — el nombre en la cabecera, cuando no hay campo propio

Y la comparación de etiquetas descarta el marcado: `Descripción`, `**Descripción**` y `- Descripción` son el mismo campo. Sin eso, dos de las tres quedarían sin reconocer.

## Dos defectos que las pruebas destaparon

**El identificador de la cabecera no contaba como campo presente.** En texto plano y Markdown el `RF-01` va en la cabecera, no en una fila, y la comprobación de completitud lo daba por ausente aunque estuviera a la vista.

**Y al corregirlo apareció una duplicación**: en LaTeX el identificador figura en la cabecera *y* en su fila, y se concatenaban dando `RF-01 RF-01`. Ahora un campo repetido con el mismo valor no se duplica.
