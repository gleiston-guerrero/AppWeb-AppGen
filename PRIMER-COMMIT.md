# Cómo incorporar esta entrega a AppWeb-AppGen

El repositorio ya existe y tiene un commit inicial con `LICENSE`, `README.md` y `.gitignore`. Estas instrucciones **añaden** sobre lo que ya hay; no reinician nada.

## 1. Clonar

```powershell
git clone https://github.com/gleiston-guerrero/AppWeb-AppGen.git
cd AppWeb-AppGen
```

## 2. Copiar los archivos entregados

Copie dentro de la carpeta clonada el contenido de la entrega. Sustituye `README.md` y `.gitignore`, y añade cuatro carpetas nuevas.

| Elemento | Acción | Nota |
|---|---|---|
| `README.md` | Sustituye | El actual tiene una sola línea |
| `.gitignore` | Sustituye | Conserva íntegra su plantilla Java y añade Node, Angular, LaTeX y herramientas |
| `LICENSE` | **No se toca** | El suyo ya es MIT y es válido |
| `.githooks/` | Nueva | Validación de mensajes de commit (TRC-10) |
| `core/` | Nueva | Módulo de nomenclatura, verificado |
| `docs/` | Nueva | 22 PDF de especificación y el `.bib` |
| `infra/` | Nueva | PostgreSQL en contenedor |

## 3. Activar la validación de mensajes

```powershell
git config core.hooksPath .githooks
```

Una vez por cada copia clonada. Git no versiona esta configuración a propósito.

## 4. Verificar antes de comprometer

```powershell
& "C:\Program Files\Git\bin\bash.exe" .githooks/commit-msg-test.sh

cd core\naming
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path out | Out-Null
javac -encoding UTF-8 -Xlint:all -d out (Get-ChildItem -Recurse src\*.java).FullName
java -cp out org.slcp.core.naming.VectorRunner naming-vectors.tsv
java -cp out org.slcp.core.naming.AdvisorVectorRunner advisor-vectors.tsv
cd ..\..
```

Tres veces `RESULTADO: VERDE`.

> **Nota sobre `sh` en Windows.** PowerShell y CMD no reconocen `sh`: Git trae su propio intérprete pero no lo añade al PATH. De ahí la ruta completa a `bash.exe` en el primer comando. Si Git está instalado en otra ubicación, localícelo con `(Get-Command git).Source` y busque `bash.exe` en la carpeta `bin` hermana. La alternativa cómoda es abrir **Git Bash** en la carpeta (clic derecho en el Explorador) y ejecutar allí `sh .githooks/commit-msg-test.sh`.
>
> Esto solo afecta a ejecutar la prueba a mano. El *hook* funciona igualmente al hacer `git commit`, porque Git lo ejecuta con su propio intérprete.

## 5. Comprobar que el hook rechaza de verdad

```powershell
git add .
git commit -m "prueba sin requisito"
```

Debe **rechazarlo** y explicar por qué. Es la primera verificación de TRC-10 en condiciones reales.

## 6. Commit y push

```powershell
git commit -m "feat(core): modulo de nomenclatura, especificacion y validacion de commits   [NAM-04, NAM-08, TRC-10]"
git push origin main
```

---

## Una corrección sugerida en `LICENSE`

Su archivo dice:

```
Copyright (c) 2026 gleiston-guerrero
```

Es válido, pero `gleiston-guerrero` es un identificador de GitHub, no un titular de derechos. Para un artefacto académico conviene el nombre legal, y la institución si la titularidad le corresponde:

```
Copyright (c) 2026 Gleiston Guerrero-Ulloa
```

o bien:

```
Copyright (c) 2026 Universidad Técnica Estatal de Quevedo
```

Es una línea, y conviene acertarla antes de que el repositorio acumule historia.
