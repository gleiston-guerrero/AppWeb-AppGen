# Corrección de finales de línea y permisos

Dos defectos detectados tras el primer *push*. Se arreglan una sola vez.

## 1. Copiar `.gitattributes` a la raíz del repositorio

Junto a `README.md` y `LICENSE`.

## 2. Ejecutar en PowerShell, dentro de `D:\Repositorios\Appweb-AppGen`

```powershell
# Marcar los hooks como ejecutables (Windows no guarda ese permiso, Git sí)
git update-index --chmod=+x .githooks/commit-msg
git update-index --chmod=+x .githooks/commit-msg-test.sh

# Renormalizar todos los archivos ya versionados según .gitattributes
git add --renormalize .

git add .gitattributes
git commit -m "fix(repo): normalizar finales de linea y permisos de los hooks   [TRC-10]"
git push origin main
```

## 3. Comprobar que el hook rechaza de verdad

Esta comprobación quedó pendiente y conviene no saltársela:

```powershell
"prueba" | Out-File -Encoding utf8 prueba.txt
git add prueba.txt
git commit -m "prueba sin requisito"
```

Debe **rechazarlo** mostrando el mensaje que explica el formato esperado. Si lo acepta, `core.hooksPath` no quedó bien configurado.

Después, limpie el archivo de prueba:

```powershell
git reset
Remove-Item prueba.txt
```

## Por qué importa

| Defecto | Consecuencia si no se corrige |
|---|---|
| `commit-msg` con finales CRLF | En una clonación nueva el intérprete falla con `bad interpreter: /bin/sh^M` y el *hook* deja de ejecutarse **sin avisar** |
| `commit-msg` sin permiso de ejecución | En Linux o macOS el *hook* no se ejecuta |

En su máquina actual el *hook* funciona, porque los archivos llegaron del zip con finales LF. El problema aparecería en la siguiente clonación, que es cuando peor se detecta.
