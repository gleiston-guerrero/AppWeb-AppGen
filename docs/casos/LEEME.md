# Requisitos de ejemplo — Granja Inteligente (SGGI)

Tres archivos con **el mismo sistema** escrito en los tres formatos que la plataforma admite. Sirven para probar la importación y para ver trabajar al validador.

| Archivo | Formato a elegir | Requisitos |
|---|---|---|
| `granja-inteligente.tex` | LaTeX con entorno de atributos | 8 funcionales + 2 no funcionales |
| `granja-inteligente.md` | Markdown con campos etiquetados | 8 funcionales + 2 no funcionales |
| `granja-inteligente.txt` | Texto plano con bloques separados | 8 funcionales + 2 no funcionales |
| `granja-inteligente.json` | JSON con lista de requisitos | 8 funcionales + 2 no funcionales |
| `granja-inteligente.yaml` | YAML con lista de requisitos | 8 funcionales + 2 no funcionales |
| `granja-inteligente.xml` | XML con elementos de requisito | 8 funcionales + 2 no funcionales |
| `granja-inteligente.csv` | Valores separados por comas | 8 funcionales + 2 no funcionales |

No son traducciones literales entre sí: cada uno cubre aspectos distintos de la misma granja, para que importar los tres en un mismo proyecto tenga sentido.

## Los defectos son deliberados

Cada archivo trae **dos o tres requisitos mal redactados a propósito**, para que compruebe que el validador los encuentra y que el sugeridor propone criterios donde faltan.

| Archivo | Requisito | Defecto sembrado | Lo que debe detectar |
|---|---|---|---|
| `.tex` | RF-06 | Dos obligaciones unidas por «y además» | `SOSPECHA` — Singular |
| `.tex` | RF-07 | «rápido» y «amigable» sin magnitud | `DEFECTO` ×2 — Verificable |
| `.tex` | RF-08 | Sin criterio de verificación | Sin criterio, con propuestas |
| `.md` | RF-07 | «debería» en lugar de «deberá» | `DEFECTO` — Conforme |
| `.md` | RF-08 | Sin criterio | Sin criterio, con propuestas |
| `.txt` | RF-07 | «Se requiere que sea notificado» — voz pasiva | `DEFECTO` — No ambiguo |
| `.txt` | RF-08 | «gestionar adecuadamente» — vago | Sin criterio, **y sin propuesta** |
| `.md` y `.yaml` | RF-07 | «debería» en lugar de «deberá» | `DEFECTO` — Conforme |
| `.json` | RF-08 | Sin criterio | Sin criterio, con propuestas |
| `.csv` | RF-08 | «gestionar adecuadamente» — vago | Sin criterio, **y sin propuesta** |
| `.xml` | — | Ninguno: los diez limpios | Sirve de control |

Ese último caso es el más interesante: ante *«gestionar adecuadamente la trazabilidad»* el sugeridor **no propone nada**, porque no hay acción observable de la que partir. Una propuesta genérica parecería una respuesta y se aceptaría sin leerla.

## Verificado

Los siete archivos se pasaron por el extractor y el validador reales: **10 requisitos extraídos de cada uno, sin duplicados y sin etiquetas desconocidas**, y cada defecto sembrado detectado por la regla que le corresponde.

El de XML no lleva ningún defecto a propósito: sirve de control. Si alguna vez produce hallazgos, el problema estará en el validador y no en el archivo.

## Dos detalles de los formatos estructurados

En `granja-inteligente.json`, el campo `actor` de RF-03 es un **arreglo** con dos valores, y llegan los dos unidos por coma. Quedarse con el primero perdería información en silencio.

En `granja-inteligente.csv`, varios enunciados **contienen comas** y van entre comillas. Es la prueba de que el lector respeta la convención: un lector improvisado partiría esas filas por la mitad.

## Cómo probarlos

1. Entre en la plataforma y abra **Requisitos** de su proyecto
2. Elija el formato — verá su descripción y un ejemplo de cómo debe verse el archivo
3. Elija el archivo correspondiente e **Importar**
4. Filtre por **Con hallazgos** y por **Sin criterio**

Los identificadores se repiten entre archivos (`RF-01` está en los tres), así que si importa los tres en el mismo proyecto, el segundo y el tercero omitirán los que ya existan. Para verlos todos, use un proyecto por formato.
