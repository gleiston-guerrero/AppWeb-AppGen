# SLCP — Módulo `core/naming`

Incremento **0.1** de la plataforma SLCP. Implementa la función única y determinista de transformación de nomenclatura de la que dependen todos los generadores de código.

## Requisitos que realiza

| Requisito | Documento | Qué implementa aquí |
|---|---|---|
| NAM-01 | SLCP-DOC-001 v1.2 | Los términos de dominio conservan su idioma; solo se transliteran a ASCII, nunca se traducen |
| NAM-04 | SLCP-DOC-001 v1.2 | La conversión canónico → destino la realiza una función única, no la plantilla |
| NAM-05 | SLCP-DOC-001 v1.2 | Colisión con palabra reservada → aborta con error explícito, jamás entrecomilla en silencio |
| NAM-06 | SLCP-DOC-001 v1.2 | Límite de longitud con abreviatura determinista y libre de colisiones |
| NAM-07 | SLCP-DOC-001 v1.2 | La forma plural procede del glosario y nunca se infiere |
| TGT-08 | SLCP-DOC-001 v1.2 | El oráculo (`naming-vectors.tsv`) se define y sella antes que la implementación |
| NAM-08 | SLCP-DOC-013 | Si el destino admite el nombre propuesto por la persona, se acepta; si no, se proponen alternativas legales y la persona elige |

**Nivel:** `[N1]` el módulo pertenece al núcleo. `[N3]` su salida son los identificadores de la aplicación generada.

## Dependencias

Ninguna. Solo JDK 21 o superior. La ausencia deliberada de dependencias externas permite compilar y verificar el incremento sin resolución de artefactos de terceros.

## Compilación y verificación

### Windows (PowerShell)

```powershell
cd core\naming
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path out | Out-Null
javac -encoding UTF-8 -Xlint:all -d out (Get-ChildItem -Recurse src\*.java).FullName
java -cp out org.slcp.core.naming.VectorRunner naming-vectors.tsv
```

### Linux o macOS

```bash
cd core/naming
rm -rf out && mkdir -p out
javac -encoding UTF-8 -Xlint:all -d out src/org/slcp/core/naming/*.java
java -cp out org.slcp.core.naming.VectorRunner naming-vectors.tsv
java -cp out org.slcp.core.naming.AdvisorVectorRunner advisor-vectors.tsv
```

### Resultado esperado

```
Vectores superados: 55      <- transformación
Vectores fallidos:  0
RESULTADO: VERDE

Vectores superados: 21      <- validación y sugerencia
Vectores fallidos:  0
RESULTADO: VERDE
```

El ejecutor devuelve código de salida `0` en verde y `1` en rojo, de modo que puede integrarse sin adaptación en una canalización de integración continua.

## Archivos

| Archivo | Función |
|---|---|
| `naming-vectors.tsv` | Oráculo de la transformación. Sellado antes de la implementación |
| `advisor-vectors.tsv` | Oráculo de la validación y la sugerencia |
| `src/.../IdentifierAdvisor.java` | Valida el nombre propuesto y sugiere alternativas legales |
| `naming-vectors.tsv.v0` | Versión previa a la enmienda A-001, conservada como evidencia |
| `oracle-amendments.md` | Registro de enmiendas al oráculo. **A-001 pendiente de ratificación** |
| `src/.../NamingTransform.java` | Transformación determinista |
| `src/.../ReservedWords.java` | Listas de exclusión por destino (semilla, ver aviso interno) |
| `src/.../Target.java`, `Kind.java` | Destinos y clases de identificador |
| `src/.../NamingException.java` | Errores con código estable verificable por el oráculo |
| `src/.../VectorRunner.java` | Ejecutor del oráculo |

## Limitaciones conocidas

1. **Listas de palabras reservadas incompletas.** Las de `ReservedWords` son una semilla verificada pero parcial. El Anexo A de SLCP-DOC-001 exige construir la lista definitiva desde las fuentes oficiales de cada destino y versionarla junto al descriptor. Esa carga corresponde al descriptor de destino, no a esta clase.
2. **Reversibilidad de la abreviatura por consulta.** La regla de NAM-06 es determinista y libre de colisiones, pero la reconstrucción del identificador completo exige la entrada correspondiente en el glosario del proyecto. La persistencia de esa correspondencia es trabajo del incremento 0.2.
3. **Sugerencias limitadas a la legalidad.** El módulo propone alternativas *legales*, no necesariamente *buenas*. Valorar si un nombre comunica bien es un juicio de significado y corresponde a la persona.
