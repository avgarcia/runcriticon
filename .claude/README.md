# `.claude/` — Automatizaciones del proyecto Runcriticon

Esta carpeta agrupa las **automatizaciones compartidas** del proyecto (skills, agents, hooks, settings) que viven en el repo y se versionan. Es complementaria al CLI de Claude Code: ubicada aquí, cualquier miembro del equipo recibe automáticamente las mismas reglas, skills y hooks al clonar.

Lo personal de cada dev (`worktrees/`, `settings.local.json`) sigue estando fuera del control de versiones (ver `.gitignore` raíz).

## Estructura

```
.claude/
├── README.md                   ← este archivo
├── settings.json               ← hooks compartidos del proyecto + mcpServers
├── skills/                     ← skills del proyecto invocables por todos
│   ├── adr-review/
│   │   └── SKILL.md            ← patrón Nivel 1 para revisión de ADRs
│   └── module-scaffold/
│       └── SKILL.md            ← scaffold de un módulo nuevo del backend
├── agents/                     ← subagents especializados
│   ├── module-architecture-reviewer.md
│   │   └── revisa diffs de PRs de módulos contra el checklist operativo
│   └── idor-hunter.md
│       └── caza IDOR (OWASP API #1) en el diff de un PR
└── hooks/                      ← scripts shell invocados por settings.json
    ├── aviso-rat.sh            ← PostToolUse: recordatorio RAT al tocar migraciones
    └── bloqueo-sensibles.sh    ← PreToolUse: bloquea edición a *.tfvars / secrets.yaml
```

## Skills (`.claude/skills/`)

Skills compartidas del proyecto. Se invocan vía `/skill-name` o por Claude automáticamente cuando aplique (según `disable-model-invocation`).

### `/adr-review` (user-only)

Aplica el patrón ultrathink de revisión Nivel 1 a un ADR del corpus, replicando el flujo aplicado los 16 ADRs aceptados entre 2026-05-27 y 2026-05-30. Bloques A-G, sugerencias priorizadas, multi-tanda de preguntas con `AskUserQuestion`, PR de revisión + PR de aceptación encadenadas.

**Cuándo invocar**: reabrir un ADR aceptado por disparador documentado en ADR-0015; auditar un ADR sospechoso; redactar un ADR nuevo aplicando Nivel 1 desde la primera versión.

### `/module-scaffold` (user-only)

Genera el scaffold completo de un módulo nuevo del backend con todos los ítems del checklist de [`docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md) ya cubiertos por construcción. Cubre paquetes, errores sellados, `IntegrationEvent` con 6+1 campos, `AutorizacionService`, casos de uso con `Either + Raise DSL`, `@AuthScope`, listeners idempotentes con `MdcRestorerForEvents`, `MetricasDelModulo`, `ConfigurationProperties`, migración Flyway con `@CategoriaRGPD`, tests stub.

**Cuándo invocar**: crear cualquiera de los 5 módulos del backend en Fase 1 (`identidad`, `club`, `planificacion`, `salud`, `auditoria`); crear un módulo futuro tras disparador.

## Agents (`.claude/agents/`)

Subagents especializados que se invocan en paralelo desde la conversación principal usando el tool `Agent`.

### `module-architecture-reviewer`

Revisa el diff de un PR de módulo contra el checklist de la guía operativa + 5 subdocumentos. Reporta violaciones bloqueantes, advertencias, e ítems del checklist no cubiertos. No edita código.

**Cómo invocar**: tras un cambio sustancial en un módulo, lanzar el agent con el rango de diff:
```
Agent(subagent_type="module-architecture-reviewer", prompt="Revisa el diff main...HEAD del módulo Planificación")
```

### `idor-hunter`

Caza IDOR (Insecure Direct Object Reference, OWASP API Security Top 10 #1) en el diff de un PR. Detecta `@ApplicationService` que carga objetos sin autorizar, `@Repository` sin `@AuthScope`, listados filtrados en memoria, falta de tests de acceso cruzado.

**Cómo invocar**: tras tocar código de autorización/casos de uso, lanzar como segunda capa de revisión:
```
Agent(subagent_type="idor-hunter", prompt="Caza IDOR en el diff del PR actual")
```

## Hooks (`.claude/hooks/`)

Scripts shell invocados automáticamente por Claude Code en eventos del tool (`PreToolUse`, `PostToolUse`). Configurados en `.claude/settings.json`.

### `aviso-rat.sh` (PostToolUse)

Tras un `Edit` o `Write` que toca una migración Flyway SQL (`backend/src/main/resources/db/migration/{modulo}/V{ts}__*.sql`), recuerda al equipo actualizar `docs/legal/rat.md` si la migración añade/modifica una tabla con datos personales.

**Política**: nunca bloquea (no es un error técnico). Solo informa.
**Cruce**: ADR-0014 D19.

### `bloqueo-sensibles.sh` (PreToolUse)

Bloquea (exit 2) ediciones a:
- `*.tfvars` con valores reales (Terraform). Excepción: `*.example.tfvars`, `terraform.tfvars.example`.
- `secrets.yaml` / `secrets.yml` (secretos hardcoded).

**Cruce**: ADR-0013 D12 (sin secretos en repo). Defensa adicional a `.gitignore` + escaneo CI.

## MCP Servers (`mcpServers` en `settings.json`)

### context7 (instalado)

Live documentation lookup. Útil para Spring Modulith, Arrow-kt, Konvert, Kotest, Angular Material 3, OpenAPI generator y demás librerías relativamente nuevas del stack.

**Invocación**: Claude lo usa automáticamente cuando necesita consultar documentación de una librería del proyecto.

### Recomendado añadir manualmente

Estas instalaciones requieren tu intervención (Claude no puede ejecutar `claude mcp add` ni `gh auth`):

#### GitHub MCP

Sustituye al wrapper de `gh` CLI por Bash. Útil para operaciones sobre PRs, status checks, issues, branch protection rules.

```bash
claude mcp add github -- npx -y @modelcontextprotocol/server-github
# Requiere GITHUB_TOKEN con scopes: repo, read:org, workflow
```

## Plugins recomendados (instalación manual)

### `anthropic-agent-skills` (skills core)

Bundle con `docx`, `xlsx`, `pdf`, `pptx`. Útil cuando lleguen las tareas:

- Generar `docs/legal/rat.md` en `.docx` para asesoría jurídica (ADR-0014 D19).
- Generar el DPIA simplificado en `.docx` (ADR-0014 D20).
- Exportar reportes de adopción a `.xlsx` (métricas de negocio del piloto — ADR-0011 D11).

**Instalación**: `/plugin marketplace add anthropic` y elegir el bundle.

## Convenciones de esta carpeta

- **Skills**: una carpeta por skill con `SKILL.md` dentro. Nombre en kebab-case.
- **Agents**: un archivo Markdown por agent en la raíz de `agents/`. Frontmatter YAML con `name`, `description`, `tools`.
- **Hooks**: scripts shell `.sh` con LF (cruce `.gitattributes`). Idempotentes y seguros con `set -euo pipefail`.
- **`settings.json`**: configuración compartida del proyecto. Se versiona. Cada dev tiene su `settings.local.json` (ignorado) para preferencias personales.
- **`worktrees/`**: trabajo en progreso de Claude Code. Ignorado por `.gitignore`.

## Cruce con CLAUDE.md raíz

Las **reglas globales** (cómo opera Claude en este repo, stack técnico, arquitectura, índice de ADRs) viven en [`CLAUDE.md`](../CLAUDE.md). Esta carpeta es la **realización ejecutable** de algunas de esas reglas: hooks que las verifican, skills que las aplican, agents que las revisan.

Si una skill o un agent contradice la `CLAUDE.md`, gana la `CLAUDE.md`.
Si una `CLAUDE.md` contradice un ADR aceptado, gana el ADR.
