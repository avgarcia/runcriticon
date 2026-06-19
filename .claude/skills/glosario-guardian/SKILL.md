---
name: glosario-guardian
description: Valida que el vocabulario de NEGOCIO en prosa, docs, wireframes, textos de UI o diffs respete el lenguaje ubicuo en castellano de Runcriticon, definido en docs/glosario.md (ADR-0008). Detecta anglicismos con sustituto canónico (coach→entrenador, student→alumno, workout→sesión) y reporta hallazgos con número de línea y sugerencia. NO audita identificadores de código (clases, funciones, columnas SQL, rutas): esos van en inglés por ADR-0008 D4 y los verifica NamingConventionArchTest. Usar cuando el usuario pida auditar vocabulario de negocio, revisar prosa/wireframes/UI por consistencia de lenguaje, o mencione "lenguaje ubicuo", "glosario" o "vocabulario de dominio".
---

# glosario-guardian

Audita un archivo, un diff o un fragmento de texto para detectar términos que violen el **lenguaje ubicuo** de Runcriticon, definido como fuente única en `docs/glosario.md` (ver ADR-0008).

## Por qué existe esta skill

El proyecto tiene una regla fuerte: el vocabulario del **negocio** está **en castellano** y se usa igual en discovery, conversaciones, wireframes, **textos de UI** y prosa de documentación. Eso evita la "deriva de traducción" — el bug clásico de tener `Coach`, `Trainer` y `Entrenador` apuntando al mismo concepto en lo que ve el usuario.

**Alcance (importante, ADR-0008 D4):** esta skill **solo** audita vocabulario de **negocio visible** — prosa, docs, wireframes y textos de UI. **No audita identificadores de código** (clases, funciones, propiedades, paquetes, columnas SQL, endpoints, rutas, selectores): esos van en **inglés** por convención y los verifica `NamingConventionArchTest`. Que un fichero `.kt`/`.ts` tenga `User`, `WeeklyPlan` o `publish()` es **correcto**, no un hallazgo.

El glosario es la fuente de verdad. Esta skill lo aplica: dado un texto, encuentra los anglicismos y propone la traducción canónica. No autocorrige — reporta para que el humano decida.

## Cuándo usar la skill

Invocar cuando:

- El usuario pide auditar un archivo / diff / texto en busca de inconsistencias de lenguaje.
- Antes de cerrar un PR que toca código de dominio, wireframes o docs nuevos.
- El usuario menciona explícitamente "glosario", "lenguaje ubicuo", "ubiquitous language", "vocabulario", "DDD terms".
- Se acaba de generar contenido nuevo (wireframe HTML, prototipo, doc) y conviene validar antes de comitear.

NO invocar para:

- **Identificadores de código** (clases, funciones, propiedades, paquetes, columnas SQL, endpoints, rutas, selectores): van en inglés por ADR-0008 D4 y los verifica `NamingConventionArchTest`. Esta skill no audita código.
- Comentarios o strings en código que **deliberadamente** son técnicos (mensajes de log para devs, claves de configuración, librerías).
- Documentación externa que se refiere a tecnologías por su nombre (Spring Boot, PostgreSQL, OpenAPI).

## Cómo opera

### 1. Cargar el glosario

Lee `docs/glosario.md` y extrae los términos canónicos. El glosario es la **fuente única**; si cambia, esta skill ajusta su comportamiento automáticamente.

### 2. Cargar el objetivo

El usuario indicará uno de:

- **Ruta a un archivo** (`.md`, `.html`, `.kt`, `.ts`, etc.).
- **Un diff** (`git diff`, salida de `git diff HEAD~1`, etc.).
- **Un fragmento de texto** pegado en la conversación.

Si el usuario no concreta, pregunta antes de escanear.

### 3. Escanear y reportar

Recorre el texto buscando coincidencias case-insensitive de los anglicismos prohibidos y reporta cada hallazgo con:

- **Línea** (si aplica).
- **Término encontrado** (con su contexto inmediato, ~5 palabras alrededor).
- **Término canónico sugerido**.
- **Severidad**: `error` (anglicismo claro con sustituto en glosario) o `warn` (ambiguo, requiere revisión humana).

### 4. Formato del reporte

Emite un reporte Markdown estructurado así:

```markdown
## Glosario-guardian — informe

**Archivo**: <ruta>
**Hallazgos**: N (X errores, Y warnings)

### Errores
- Línea 42 · `coach` → **entrenador**
  > "...the coach can publish the plan..."
- Línea 88 · `workout` → **sesión**
  > "...this workout includes 5 series..."

### Warnings (revisar manualmente)
- Línea 110 · `runner` → posiblemente **alumno** (depende del contexto)
  > "...notify every runner in the group..."

### Sin hallazgos en
- Funciones de framework, imports, identificadores técnicos (Spring, RxJS, etc.)
```

Si no hay hallazgos, dilo claramente: *"Sin anglicismos detectados. El texto respeta el glosario."*

## Mapeo de anglicismos a términos canónicos

Esta tabla resume las equivalencias más frecuentes. La fuente de verdad sigue siendo `docs/glosario.md` — si un término no está claro aquí, consúltalo allí.

| Anglicismo (a detectar)                     | Canónico (sugerir)                                 | Notas                                                                         |
|---------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------|
| `coach`, `trainer`                          | `entrenador`                                       | Rol del dominio.                                                              |
| `student`, `athlete`, `runner`              | `alumno`                                           | El corredor del club. `runner` puede ser ambiguo en docs genéricos → warning. |
| `admin` (sin prefijo)                       | `admin` ✅                                          | Permitido — está en el glosario tal cual.                                     |
| `group`                                     | `grupo`                                            | Una consulta nombrada sobre tags.                                             |
| `workout`, `training session`               | `sesión`                                           | Unidad de un plan.                                                            |
| `plan`                                      | `plan` ✅                                           | Permitido.                                                                    |
| `weekly plan`, `training plan`              | `plan semanal`                                     |                                                                               |
| `pace`                                      | `ritmo`                                            | Modelado como `{tipo, valor}`.                                                |
| `tag`, `tags`                               | `tag` ✅                                            | Permitido — entidad de primera clase, queda en inglés.                        |
| `tag key`, `key`                            | `TagKey` (en código) / `clave de tag` (en prosa)   |                                                                               |
| `tag value`, `value`                        | `TagValue` (en código) / `valor de tag` (en prosa) |                                                                               |
| `taxonomy`                                  | `taxonomía`                                        |                                                                               |
| `race`, `objective`, `target race`          | `carrera` / `objetivo`                             | Según contexto.                                                               |
| `override`                                  | `override de grupo`                                | Permitido (técnico del dominio).                                              |
| `session report`, `workout report`          | `reporte de sesión`                                |                                                                               |
| `report`                                    | `reporte`                                          |                                                                               |
| `alert`                                     | `alerta`                                           |                                                                               |
| `club health`, `health dashboard`           | `salud del club`                                   |                                                                               |
| `invitation`, `invite`                      | `invitación`                                       |                                                                               |
| `magic link`                                | `magic link` ✅                                     | Permitido — está en el glosario.                                              |
| `bounded context`                           | `módulo` o `bounded context` ✅                     | Ambas válidas; preferir `módulo` en código.                                   |
| `domain event`                              | `evento de dominio`                                |                                                                               |
| `projection`, `read model`                  | `proyección` / `read model` ✅                      | Ambas válidas.                                                                |
| `snapshot`                                  | `snapshot` ✅                                       | Permitido.                                                                    |
| `publish`                                   | `publicar`                                         |                                                                               |
| `personalization`                           | `personalización`                                  |                                                                               |
| `club_id`                                   | `club_id` ✅                                        | Permitido — identificador técnico.                                            |

### Tipos de sesión (catálogo canónico del MVP)

Estos NO se traducen al inglés. Si aparecen los equivalentes en inglés, son errores:

| Inglés (rechazar) | Canónico |
|-------------------|----------|
| `easy run`, `recovery run` | `rodaje` |
| `intervals`, `repeats`     | `series` |
| `tempo run`                | `tempo` |
| `long run`                 | `tirada larga` |
| `fartlek`                  | `fartlek` ✅ |
| `hills`, `hill repeats`    | `cuestas` |
| `progression run`          | `progresivo` |
| `strength`, `cross-training` | `fuerza` / `cross` |
| `race`                     | `competición` (cuando es tipo de sesión) |
| `rest`, `rest day`         | `descanso` |

## Heurísticas para evitar falsos positivos

1. **Ignorar identificadores técnicos** — nombres de librerías (`React`, `Angular`, `Spring`), métodos HTTP (`POST`, `GET`), comandos (`git`, `npm`), siglas (`API`, `JWT`, `RBAC`).
2. **Ignorar todo identificador de código** (clases, funciones, variables, propiedades, columnas SQL, endpoints, rutas, selectores) y los bloques de código: van en inglés por ADR-0008 D4 y no son competencia de esta skill. Solo se audita el **texto en prosa y el contenido visible al usuario** — incluidos los strings de UI dentro del código (labels, mensajes), que sí van en castellano.
3. **Imports y nombres de fichero** no se auditan (rutas como `src/auth/AuthService.ts`).
4. **Strings dentro de comillas** que parezcan claves de configuración (`"role"`, `"id"`) no se auditan, pero strings que parezcan contenido visible al usuario sí.
5. **Términos ambiguos** (`runner`, `member`) → emitir como **warning**, no como error. Que decida el humano.
6. **No auditar texto de citas o referencias externas** (URLs, nombres de proveedores: AWS, Resend, GitHub).

## Reglas duras

- **Nunca autocorrijas.** Solo reporta. Que la decisión de cambiar sea humana — un anglicismo puede ser intencional (ej. un nombre propio).
- **No inventes términos canónicos.** Si el glosario no tiene un equivalente claro, marca como `warning` y sugiere consultar `docs/glosario.md`.
- **El glosario manda sobre esta skill.** Si esta tabla diverge del glosario porque el glosario se actualizó, la versión del glosario gana — recoge el cambio al releer el archivo.
- **No reescribas el contenido del archivo.** Solo emite el informe.

## Ejemplo de invocación

**Usuario:** *"Audita docs/wireframes/vista-hoy.html, creo que se me han colado anglicismos."*

**Comportamiento esperado:**

1. Lee `docs/glosario.md`.
2. Lee `docs/wireframes/vista-hoy.html`.
3. Escanea y emite el reporte en formato Markdown.
4. Si encuentra hallazgos, los lista con línea, contexto y sugerencia. Si no, lo dice explícitamente.
5. Nunca toca el HTML.

**Usuario:** *"Revisa este diff:"* (pega `git diff`)

**Comportamiento esperado:**

1. Lee `docs/glosario.md`.
2. Parsea el diff (solo las líneas añadidas, marcadas con `+`).
3. Para cada línea añadida con anglicismo, reporta el archivo + número de línea (extraídos de las cabeceras `@@ -X,Y +X,Y @@` y `+++ b/<path>`).
4. Emite reporte agrupado por archivo.
