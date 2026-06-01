# `.claude/` — Automatizaciones del proyecto Runcriticon

Esta carpeta agrupa las **automatizaciones compartidas** del proyecto (skills, agents, hooks, settings) que viven en el repo y se versionan. Ubicadas aquí, cualquier miembro del equipo recibe automáticamente las mismas reglas, skills y hooks al clonar.

Lo personal de cada dev (`worktrees/`, `settings.local.json`, `plans/`) queda fuera del control de versiones (ver `.gitignore` raíz).

## Estructura

```
.claude/
├── README.md                          ← este archivo
├── settings.json                      ← hooks compartidos + 6 MCP servers
├── skills/
│   ├── adr-review/                    ← patrón Nivel 1 para revisión de ADRs (user-only)
│   ├── module-scaffold/               ← scaffold de un módulo nuevo (user-only)
│   ├── runbook-generator/             ← genera runbooks operativos (both)
│   ├── integration-event-creator/     ← crea integration event + 4 artefactos (user-only)
│   ├── spring-modulith-debug/         ← interpreta errores de Modulith / outbox (both)
│   ├── flyway-migration-checker/      ← verifica migraciones compatibles hacia atrás (both)
│   └── disparador-checker/            ← conocimiento de fondo de aplazamientos (claude-only)
├── agents/
│   ├── module-architecture-reviewer.md  ← revisa diffs de módulo contra el checklist
│   ├── idor-hunter.md                   ← caza IDOR (OWASP API #1)
│   ├── event-contract-reviewer.md       ← verifica los 4 artefactos de cada evento
│   └── adr-coherence-scanner.md         ← detecta contradicciones cruzadas en el corpus
└── hooks/
    ├── bloqueo-sensibles.sh           ← PreToolUse: bloquea .env / *.tfvars / secrets.yaml
    ├── bloqueo-adr-aceptado.sh        ← PreToolUse: bloquea edición de ADRs Aceptados
    ├── gitleaks-scan.sh               ← PostToolUse: escaneo de secretos en archivo editado
    ├── lint-kotlin.sh                 ← PostToolUse: ktlintFormat + detekt
    ├── lint-frontend.sh               ← PostToolUse: Prettier + ESLint
    ├── valida-json-schema.sh          ← PostToolUse: valida JSON Schemas de eventos
    ├── aviso-rat.sh                   ← PostToolUse: recordatorio RAT al tocar migraciones
    └── contexto-modulo.sh             ← SessionStart: precarga docs del módulo activo
```

## Skills (`.claude/skills/`)

| Skill | Invocación | Qué hace |
|---|---|---|
| **`/adr-review`** | user-only | Patrón ultrathink de revisión Nivel 1 a un ADR: bloques A-G, multi-tanda de preguntas, PRs revisión + aceptación encadenadas. |
| **`/module-scaffold`** | user-only | Scaffold completo de un módulo del backend con los 30+ ítems del checklist cubiertos por construcción. |
| **`/runbook-generator`** | both | Genera un runbook operativo en `docs/runbooks/` cruzando con el ADR que lo invoca. |
| **`/integration-event-creator`** | user-only | Crea un integration event público + sus 4 artefactos (clase, JSON Schema, test de contrato, stubs de listeners). |
| **`/spring-modulith-debug`** | both | Traduce errores de fronteras de Modulith y del outbox a fixes coherentes con events-first. |
| **`/flyway-migration-checker`** | both | Verifica que una migración Flyway sea compatible hacia atrás y siga las reglas de persistencia. |
| `disparador-checker` | claude-only | Conocimiento de fondo: recuerda los disparadores de los aplazamientos (ADR-0015) antes de reabrir nada. No invocable por el usuario. |

## Agents (`.claude/agents/`)

Subagents especializados invocables con el tool `Agent` (en paralelo a la conversación principal).

| Agent | Qué revisa | Tools |
|---|---|---|
| **`module-architecture-reviewer`** | Diff de un PR de módulo contra el checklist en 10 bloques. | Bash, Glob, Grep, Read, WebFetch |
| **`idor-hunter`** | IDOR (OWASP API #1) con 6 patrones + tests de acceso cruzado faltantes. | Bash, Glob, Grep, Read |
| **`event-contract-reviewer`** | Los 4 artefactos de cada integration event + propagación de `traceparent`. | Bash, Glob, Grep, Read |
| **`adr-coherence-scanner`** | Contradicciones cruzadas, premisas rotas, cruces colgantes, divergencias con ADR-0015. | Bash, Glob, Grep, Read |

Ejemplo de invocación:

```
Agent(subagent_type="module-architecture-reviewer", prompt="Revisa el diff main...HEAD del módulo Planificación")
Agent(subagent_type="idor-hunter", prompt="Caza IDOR en el diff del PR actual")
Agent(subagent_type="event-contract-reviewer", prompt="Verifica los contratos de eventos tras tocar api/events")
Agent(subagent_type="adr-coherence-scanner", prompt="Audita la coherencia del corpus de ADRs")
```

## Hooks (`.claude/hooks/`)

Scripts shell invocados por Claude Code en eventos de tool. Configurados en `.claude/settings.json`. Todos son **best-effort**: si la herramienta (gradle, gitleaks, npx, python3) no está disponible —como en H0 pre-Bloque 2A—, no hacen nada.

### PreToolUse (Edit|Write)

| Hook | Efecto |
|---|---|
| `bloqueo-sensibles.sh` | **Bloquea** (exit 2) edición de `.env`, `*.tfvars` reales, `secrets.yaml`. Excepción: `*.example`. (ADR-0013 D12) |
| `bloqueo-adr-aceptado.sh` | **Bloquea** edición de ADRs en estado Aceptado salvo en rama `feature/revision-adr-NNNN`. |

### PostToolUse (Edit|Write)

| Hook | Efecto |
|---|---|
| `gitleaks-scan.sh` | Escanea el archivo editado; **bloquea** (exit 2) si detecta un secreto. (ADR-0010 D7) |
| `lint-kotlin.sh` | `ktlintFormat` + `detekt` sobre `.kt`/`.kts`. Informativo. (ADR-0012 / backend) |
| `lint-frontend.sh` | Prettier + ESLint `--fix` sobre `.ts`/`.html`/`.scss` del frontend. (ADR-0012 D11) |
| `valida-json-schema.sh` | Valida JSON Schemas de eventos (válido + `$schema` 2020-12 + 6 campos). Informativo. (ADR-0007 D11) |
| `aviso-rat.sh` | Recuerda actualizar `docs/legal/rat.md` al tocar una migración SQL. Informativo. (ADR-0014 D19) |

### SessionStart

| Hook | Efecto |
|---|---|
| `contexto-modulo.sh` | Detecta el módulo con cambios y precarga al contexto qué subdocumentos de arquitectura leer. |

## MCP Servers (`mcpServers` en `settings.json`)

| MCP | Estado | Prerrequisitos |
|---|---|---|
| **context7** | ✅ activo | Ninguno (npx). Documentación viva de Spring Modulith, Arrow-kt, Angular Material, etc. |
| **filesystem** | ✅ activo | Ninguno (npx). Búsqueda y operaciones de archivo sobre la raíz del repo. |
| **postgres** | 🟡 activo con BD local | `docker-compose up -d` (Postgres en `:5432`). Connection string local con password fake. Inspección de esquemas/tablas/outbox. |
| **playwright** | 🟡 útil desde Bloque 5 | Ninguno (npx). Navegador headless para validar pantallas Angular + axe-core (ADR-0012 D21). |
| **aws** | 🔴 inerte hasta Bloque 4 | `uv` instalado + credenciales AWS (`AWS_PROFILE=runcriticon`). Inerte hasta aprovisionar AWS. |
| **sentry** | 🔴 inerte hasta H1+ | `SENTRY_AUTH_TOKEN`. Inerte hasta activar error tracking (ADR-0011 D23). |

**Importante sobre los MCP con credenciales** (`aws`, `sentry`): están **configurados pero inertes** hasta que existan los servicios y se definan las variables de entorno. Un MCP que falla al arrancar no rompe Claude Code: aparece como no disponible. Para activarlos:

```bash
# AWS (Bloque 4, cuando exista la cuenta)
#   - instalar uv: https://docs.astral.sh/uv/
#   - configurar perfil: aws configure --profile runcriticon
#   - el MCP usa la cadena de credenciales estándar; no hay secretos en settings.json

# Sentry (H1+, si se activa el disparador de ADR-0011 D23)
#   - exportar el token en settings.local.json o en el entorno:
#     export SENTRY_AUTH_TOKEN=...
```

**Decisión de versionado**: los MCP sin credenciales y el postgres local (password fake documentado) van en `settings.json` compartido. Los tokens reales **nunca** se commitean: se inyectan por env var o por `settings.local.json` personal de cada dev.

## Plugins recomendados (instalación manual)

Claude no puede ejecutar `/plugin` por ti; estos requieren tu intervención.

### `frontend-design`

Genera HTML/CSS con estilo Material de alta fidelidad. **Cuándo**: al arrancar los mockups hi-fi de las pantallas del camino crítico de Fase 1 (editor de plan semanal, vista "hoy" del alumno, constructor de grupos, reporte de sesión, mis marcas) — que se aplazaron tras cerrar H0.

```
/plugin marketplace add anthropics/claude-code
/plugin install frontend-design
```

### `anthropic-skills:setup-cowork`

Setup guiado de Cowork: instala plugins según el rol, conecta herramientas, prueba una skill. **Cuándo**: si el equipo crece y trabaja en paralelo con Claude desde varias máquinas, para unificar la configuración del equipo.

```
/plugin install anthropic-skills
# luego: /setup-cowork
```

> Otros plugins valorados pero no instalados: `commit-commands`, `pr-review-toolkit` (incluye `silent-failure-hunter`, útil para listeners y fallback de Postmark), `mcp-builder`. Pídelos cuando lleguen al bloque correspondiente.

## Convenciones de esta carpeta

- **Skills**: una carpeta por skill con `SKILL.md`. Frontmatter con `name`, `description`, y control de invocación (`disable-model-invocation: true` para user-only; `user-invocable: false` para claude-only).
- **Agents**: un archivo Markdown por agent. Frontmatter con `name`, `description`, `tools`. **No editan código**, solo reportan.
- **Hooks**: scripts `.sh` con LF (`.gitattributes`), `set -uo pipefail`, **best-effort** (saltan si falta la herramienta), idempotentes.
- **`settings.json`**: configuración compartida del proyecto. Cada dev tiene su `settings.local.json` (ignorado) para credenciales y preferencias.

## Jerarquía de autoridad

1. **ADR aceptado** — fuente de verdad de cualquier decisión arquitectónica.
2. **`CLAUDE.md`** — reglas globales operativas. Si contradice un ADR, gana el ADR.
3. **Skills / agents / hooks** de esta carpeta — realización ejecutable de las reglas. Si una contradice la `CLAUDE.md`, gana la `CLAUDE.md`.
