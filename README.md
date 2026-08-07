# SLCP — Plataforma de Orquestación del Ciclo de Vida del Software

Aplicación web que genera aplicaciones web a partir de una especificación de requisitos.

**Repositorio:** https://github.com/gleiston-guerrero/AppWeb-AppGen  
**Licencia:** MIT (ver `LICENSE`)
**Institución:** Universidad Técnica Estatal de Quevedo

---

## Estado actual

| Módulo | Estado | Verificación |
|---|---|---|
| `core/naming` | Consolidado | 76 vectores en verde, sin dependencias externas |
| Metamodelo del ciclo de vida | En construcción | — |
| Servicio (Spring Boot) | No iniciado | Requiere su máquina |
| Interfaz (Angular) | No iniciado | Requiere su máquina |

La especificación completa está en `docs/`. El documento maestro es `slcp-doc-001.pdf`.

---

## Qué necesita instalado

Solo cuatro cosas. Todo lo demás lo descarga el propio proyecto.

| Herramienta | Versión mínima | Para qué | Cómo comprobar |
|---|---|---|---|
| **JDK** | 21 | Compilar y ejecutar el núcleo y el servicio | `java -version` y `javac -version` |
| **Node.js** | 20 LTS | Construir y servir la interfaz | `node --version` |
| **Docker Desktop** o **Podman** | cualquiera reciente | Levantar PostgreSQL sin instalarlo | `docker --version` |
| **Git** | cualquiera reciente | Clonar y versionar | `git --version` |

**No necesita instalar Maven.** El proyecto incluirá el *wrapper* (`mvnw`), que se descarga solo la primera vez.

**No necesita instalar PostgreSQL.** Se levanta en contenedor con un solo comando.

---

## Arranque (cuando existan los módulos de servicio e interfaz)

### 1. Clonar

```powershell
git clone https://github.com/gleiston-guerrero/AppWeb-AppGen.git
cd AppWeb-AppGen
git config core.hooksPath .githooks
```

La tercera línea activa la validación de mensajes de *commit* y hay que ejecutarla
una sola vez por cada copia clonada. Git no comparte los *hooks* entre copias.

### 2. Levantar la base de datos

```powershell
docker compose -f infra/docker-compose.yml up -d
```

### 3. Ejecutar el núcleo (esto ya funciona hoy)

```powershell
cd core\naming
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path out | Out-Null
javac -encoding UTF-8 -Xlint:all -d out (Get-ChildItem -Recurse src\*.java).FullName
java -cp out org.slcp.core.naming.VectorRunner naming-vectors.tsv
java -cp out org.slcp.core.naming.AdvisorVectorRunner advisor-vectors.tsv
```

Resultado esperado: `RESULTADO: VERDE` en ambos.

### 4. Servicio e interfaz (aún no disponibles)

```powershell
# Servicio  -> http://localhost:8080
.\mvnw spring-boot:run

# Interfaz  -> http://localhost:4200
cd web
npm install
npm start
```

---

## Estructura

```
AppWeb-AppGen/
├── LICENSE              Licencia MIT
├── .githooks/           Validación de mensajes de commit (TRC-10)
├── core/                Núcleo de la plataforma, sin dependencias externas
│   └── naming/          Transformación y validación de identificadores
├── docs/                Especificación (PDF y fuentes LaTeX)
├── infra/               Composición de contenedores para desarrollo
└── web/                 Interfaz Angular (pendiente)
```

---

## Convención de mensajes de commit

Obligatoria por el requisito TRC-10: todo *commit* debe referenciar el requisito que lo motiva.

```
<tipo>(<ámbito>): <descripción>   [<ID-REQUISITO>]

Ejemplo:
feat(naming): validar nombres propuestos por la persona   [NAM-08]
fix(naming): corregir sensibilidad a mayúsculas por destino   [NAM-05]
docs(spec): incorporar la familia VER   [VER-01..VER-23]
```

Un *commit* sin identificador de requisito es rechazado por `.githooks/commit-msg`.

El propio *hook* tiene su oráculo, que puede ejecutarse en cualquier momento:

```powershell
sh .githooks/commit-msg-test.sh
```

Resultado esperado: 13 comprobaciones superadas, `RESULTADO: VERDE`.

---

## Cómo se verifica antes de integrar

| Comprobación | Requisito | Estado |
|---|---|---|
| Oráculos del núcleo en verde | TGT-08 | Activo |
| Mensaje de commit con requisito | TRC-10 | Activo (`.githooks/commit-msg`) |
| Análisis estático y de dependencias | VER-08 | Pendiente de canalización |
| Inventario de componentes (SPDX y CycloneDX) | CON-05 | Pendiente de canalización |
| Conformidad arquitectónica | VER-06 | Pendiente de la vista de desarrollo |
