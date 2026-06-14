# ADR-NNNN — [Título de la decisión]

- **Estado**: Propuesto
- **Fecha**: YYYY-MM-DD
- **Decisores**: [quién participa en la decisión]
- **Relacionado con**: [ADRs, riesgos o documentos relacionados — ej. ADR-0007 D6, R10, vision.md]

## Índice de sub-decisiones

[Si el ADR fija **una decisión arquitectónica compuesta** con sub-decisiones, recoger el patrón Nivel 1 con párrafo introductorio + tabla. Si es una decisión simple (1-2 puntos), saltar al Contexto directamente y mantener la estructura más ligera.]

Este ADR fija una **decisión arquitectónica compuesta** sobre [tema]. Las N sub-decisiones se agrupan en X áreas:

- **Área 1 (D1-Dk)** — [resumen breve].
- **Área 2 (Dk+1-Dn)** — [resumen breve].

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Título corto](#d1)                                                                | Estratégica  |
| D2  | [Título corto](#d2)                                                                | Operativa    |
| ... | ...                                                                                | ...          |

## Contexto y problema

[Describe la situación y la fuerza que obliga a decidir. 2-4 frases. ¿Qué problema resolvemos? ¿Por qué hay que decidir esto ahora?]

## Premisas heredadas (no se revisan en este ADR)

[Lista las premisas que vienen como **input cerrado** de otros ADRs aceptados o del contexto del proyecto. **No se revisan aquí**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo. Cruzar con número de ADR + sub-decisión cuando aplique.]

- **[Premisa 1]** (ADR-XXXX DN). [Una frase de contexto si hace falta.]
- **[Premisa 2]** (ADR-XXXX DN). [...]
- ...

## Requisitos no funcionales

[Tabla con dimensiones cuantitativas o cualitativas que la decisión debe satisfacer. Si los NFRs vienen heredados de otros ADRs, marcarlos como tal.]

| Dimensión | Valor objetivo |
|---|---|
| [Dimensión 1] | [Cifra concreta] |
| [Dimensión 2] | [Cifra concreta] |
| ... | ... |

## Drivers de la decisión

[Los criterios que importan para elegir. Ejemplos:]

- [Driver 1 — ej. el equipo debe poder mantenerlo sin contratar perfiles nuevos]
- [Driver 2 — ej. coste de infraestructura bajo en fase beta]
- [Driver 3 — ej. compatible con el modelo de datos de tags]

## Opciones consideradas

- **Opción A** — [nombre]
- **Opción B** — [nombre]
- **Opción C** — [nombre]

### Opción A — [nombre]

[Descripción breve.]

- 👍 [ventaja]
- 👍 [ventaja]
- 👎 [inconveniente]

### Opción B — [nombre]

[Descripción breve.]

- 👍 [ventaja]
- 👎 [inconveniente]

### Opción C — [nombre]

[Descripción breve.]

- 👍 [ventaja]
- 👎 [inconveniente]

## Decisión

**Opción [X] — [nombre breve].** [En una o dos frases, por qué gana frente a las demás dados los drivers.]

[Si el ADR tiene sub-decisiones, introducir aquí: "Las N sub-decisiones desarrolladas a continuación. M son **estratégicas** (...); el resto son **operativas** y derivan o implementan las anteriores."]

<a id="d1"></a>
### D1 — [Título corto]

[Cuerpo de la sub-decisión. Argumento, alcance, cualquier matiz operativo.]

<a id="d2"></a>
### D2 — [Título corto]

[Cuerpo de la sub-decisión.]

<a id="d3"></a>
### D3 — [Título corto]

[Cuerpo de la sub-decisión.]

[... continuar con tantas sub-decisiones como haga falta. Mantener `<a id="dN"></a>` en línea aparte antes del `### DN — Título`. Si la sub-decisión es un aplazamiento consciente (ADR-0015), usar `A1`, `A2`, ... con `<a id="aN"></a>`.]

## Lo que este ADR no decide

[Opcional. Delimita explícitamente el alcance: temas adyacentes que quedan fuera y dónde se deciden (otro ADR, tarea de diseño, implementación). Evita que el lector asuma que el silencio es decisión. Ej. ADR-0012 §"Lo que este ADR no decide". Si no aporta, eliminar la sección.]

- [Tema fuera de alcance] — [dónde se decide].

## Consecuencias

### Positivas

- [Lo que mejora o se desbloquea con esta decisión.]
- [...]

### Negativas / coste asumido

- [Lo que empeora, el coste o la deuda que se acepta conscientemente.]
- [...]

### Riesgos y mitigaciones

- **[Riesgo derivado]** → [cómo se mitiga].
- **[Riesgo derivado]** → [cómo se mitiga].

## Criterios de éxito

[Opcional — recomendado en ADRs estratégicos. Tabla de métricas medibles con umbral, sub-decisión atribuible y cómo medirla, más la regla de interpretación (cuántas en rojo obligan a reabrir). Ej. ADR-0001 §"Criterios de éxito a 6 meses". Si la decisión no es verificable con datos, eliminar la sección.]

| # | Métrica | Umbral verde | Atribuible a | Cómo medirla |
|---|---------|--------------|--------------|--------------|
| M1 | [métrica] | [cifra] | D[N] | [fuente del dato] |

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian, este ADR se revisita.
- [Disparadores explícitos de reapertura, si aplica. Si es un aplazamiento, cruzar a `ADR-0015` para que entre en el índice maestro.]
- **Revisión periódica**: este ADR se revisa cada **X meses** o cuando [condición concreta].
- [Cualquier otra cosa: enlaces, fecha de revisión prevista, condiciones bajo las que reabrir.]

---

> **Patrón Nivel 1**: este template implementa el patrón consolidado entre 2026-05-27 y 2026-05-30 sobre el corpus inicial (ADR-0001 a ADR-0016). Los rasgos clave son: **índice de sub-decisiones con tabla** (#, Sub-decisión, Capa), **premisas heredadas** explícitas que no se revisan, **NFRs propios cuantitativos** (siempre **antes** de Drivers), **sub-decisiones numeradas DN** (o `AN` para aplazamientos) con `<a id>` en línea aparte para poder cruzar desde otros ADRs (`ver ADR-0009 D11`), **cruces explícitos** a las sub-decisiones de los ADRs invocados.
>
> Si la decisión es simple (1-2 puntos), no es obligatorio aplicar todo el patrón: índice + premisas + sub-decisiones pueden omitirse y el cuerpo de la decisión va directamente bajo el `## Decisión`. La estructura de Contexto / Drivers / Opciones / Consecuencias / Notas se mantiene siempre.
>
> **Ciclo de vida del Estado** (ver [`README.md`](README.md) §Estados): `Propuesto` → `Aceptado` → `Reemplazado por ADR-NNNN` / `Obsoleto`. Las revisiones se anotan en la línea **Fecha**, no en Estado: `YYYY-MM-DD · revisado YYYY-MM-DD (resumen de cambios) · **aceptado YYYY-MM-DD**`. Un ADR aceptado no se reescribe: revisiones posteriores añaden otra marca `· revisado ...` y pasan por PR (`/adr-review`).
