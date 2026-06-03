---
name: adr-creator
description: Scaffold a new Architecture Decision Record (ADR) under docs/adr/ in the runcriticon MADR/log4brains format. Use this skill whenever the user wants to record an architectural decision, says they "need a new ADR", asks to "document a decision", proposes changing existing behavior that crosses module boundaries, or describes a tradeoff between options that deserves to be captured. The skill assigns the next correlative number, fills the template, sets status to Propuesto, dates it today, and updates docs/adr/README.md's index table. Always invoke this skill instead of writing an ADR from scratch when the user asks for one.
---

# adr-creator

Crea un nuevo ADR en `docs/adr/` siguiendo el formato MADR ligero que usa Runcriticon (ver `docs/adr/template.md` y los 15 ADRs existentes como referencia).

## Por qué existe esta skill

Cada ADR del proyecto sigue una estructura fija: numeración correlativa de 4 dígitos, nombre `NNNN-titulo-en-kebab-case.md`, frontmatter con Estado/Fecha/Decisores/Relacionado-con, secciones obligatorias (Contexto, Drivers, Opciones, Decisión, Consecuencias, Notas), y aparece también en `docs/adr/README.md` como entrada de la tabla índice. Hacer esto a mano cada vez es repetitivo y propenso a errores (saltar un número, olvidar el índice, alterar el orden de secciones). Esta skill automatiza el scaffolding y deja al humano solo la parte de pensar.

## Cuándo usar la skill

Invocar cuando:

- El usuario pide explícitamente "crear un ADR" / "nuevo ADR" / "documentar una decisión de arquitectura".
- El usuario describe una decisión de arquitectura que merece quedar registrada (cambios que cruzan módulos, elección entre alternativas con tradeoff, decisión costosa de revertir).
- Se cierra una discusión técnica con una conclusión que afecta a la pila, dominio, seguridad o despliegue.

NO invocar para cambios triviales (renombrar una variable, ajustar un texto). Los ADRs son para decisiones con consecuencias.

## Workflow

### 1. Determinar el siguiente número correlativo

Ejecuta lo siguiente para obtener el número libre:

```bash
ls docs/adr/[0-9][0-9][0-9][0-9]-*.md | sort | tail -1
```

Toma el número de ese fichero, súmale 1, y formatea a 4 dígitos con ceros a la izquierda (`0016`, `0017`...).

Si no hay ningún ADR aún, usar `0001`. Pero el proyecto ya tiene 15 ADRs (`0001`-`0015`) — el siguiente será `0016` salvo que se haya añadido otro entre medias.

### 2. Recopilar la información mínima del usuario

Si el usuario no la ha dado ya en la conversación, pregunta de forma concisa:

- **Título** — frase corta que nombra **la decisión, no el problema**. Ej. *"Usar Postgres como base de datos"* ✅, *"¿Qué base de datos?"* ❌.
- **Contexto** — qué situación obliga a decidir esto ahora (2-4 frases).
- **Opciones consideradas** — al menos 2, idealmente 3. Si solo hay una, realizar las preguntas necesarias para poder ofrecer alguna alternativa.
- **Opción elegida** y por qué.
- **ADRs/documentos relacionados** — referencias cruzadas.

Lo demás (drivers, consecuencias, riesgos) puedes derivarlo o redactarlo a partir del contexto de la conversación. Pregunta solo si falta algo crítico.

### 3. Crear el fichero

- Nombre: `docs/adr/NNNN-titulo-en-kebab-case.md` (kebab-case con guiones simples, sin acentos ni eñes — ej. `0016-cambio-de-stack-de-emails.md`).
- Copia la estructura de `docs/adr/template.md` y rellena.
- **Estado inicial**: `Propuesto` (siempre — los ADR pasan a `Aceptado` por PR, nunca nacen aceptados; ver `docs/adr/README.md`).
- **Fecha**: la de hoy en formato `YYYY-MM-DD`.
- **Decisores**: por defecto `Negocio (Antonio) · futuro equipo técnico` (lo que usan casi todos los ADR existentes). Ajusta si el usuario indica otro.

### 4. Estructura del cuerpo

Sigue **exactamente** las secciones del template, en este orden:

```markdown
# ADR-NNNN — [Título]

- **Estado**: Propuesto
- **Fecha**: YYYY-MM-DD
- **Decisores**: ...
- **Relacionado con**: ...

## Contexto y problema
...

## Drivers de la decisión
- ...

## Opciones consideradas
- **Opción A** — ...
- **Opción B** — ...

### Opción A — ...
[descripción + bullets 👍/👎]

### Opción B — ...
[descripción + bullets 👍/👎]

## Decisión
[la opción elegida + por qué]

## Consecuencias

### Positivas
- ...

### Negativas / coste asumido
- ...

### Riesgos y mitigaciones
- [Riesgo] → [mitigación].

## Notas
[opcional]
```

Detalles de estilo observados en los ADR existentes (mantenlos):

- Usar `👍` y `👎` (no ✅/❌) en las listas de pros/contras de cada opción.
- Cuando se referencian ADR-XXXX, escribirlos así (con guion y cuatro dígitos): `ADR-0007`, no `ADR7` ni `ADR #7`.
- Las opciones se nombran `Opción A`, `Opción B`, `Opción C` (mayúscula y letra).
- Negrita en `**Drivers de la decisión**`, `**Decisión**`, etc., solo en los nombres de opciones dentro de bullets — los títulos `##` no llevan negrita.
- Tono en español neutro, frases cortas, voz activa. Mira `0001-stack-aplicacion-web.md` o `0009-modelo-de-autorizacion.md` como referencia de tono.

### 5. Actualizar el índice en `docs/adr/README.md`

Añade una fila a la tabla "Índice de ADRs" **respetando el orden numérico**. Formato exacto:

```markdown
| [NNNN](NNNN-titulo-en-kebab-case.md) | Título tal cual aparece en el H1 | Propuesto | YYYY-MM-DD |
```

(El README explica que esta tabla se mantiene a mano — el sitio navegable lo genera log4brains.)

### 6. Verificar y resumir

Tras crear el ADR:

1. Lee de nuevo el fichero creado para confirmar que el formato es correcto.
2. Reporta al usuario: ruta del nuevo ADR, número asignado, y un resumen de 1-2 frases de la decisión registrada.
3. Recuérdale que el ADR nace en `Propuesto` y pasa a `Aceptado` al aprobarse la PR.

## Reglas duras

- **Nunca inventes el número** sin haberlo verificado con `ls`. Saltarse uno o duplicarlo es un error que se arrastra.
- **Nunca modifiques un ADR ya aceptado.** Si la decisión cambia, se crea un ADR nuevo que la reemplaza (y se marca el viejo como `Reemplazado por ADR-NNNN`). Esto es política del proyecto (ver `docs/adr/README.md`).
- **Nunca uses inglés en el contenido del ADR.** El proyecto tiene lenguaje ubicuo en español (ver `docs/glosario.md`). Términos como *coach*, *student*, *workout* serían bugs — usar *entrenador*, *alumno*, *sesión*. Si tienes la skill `glosario-guardian` disponible, considera invocarla al terminar para validar el texto.
- **No sobre-relleneis secciones.** Si una opción no tiene un 👎 obvio, no inventes uno por simetría. Si no hay riesgos, esa sección puede ser breve.

## Ejemplo de invocación

**Usuario:** *"Quiero documentar la decisión de usar Resend en vez de SendGrid para los emails transaccionales."*

**Respuesta esperada de la skill:**

1. Detecta que el último ADR es `0015`, así que el nuevo será `0016`.
2. Pregunta brevemente solo lo que falte (drivers, opciones alternativas, riesgos).
3. Crea `docs/adr/0016-cambio-proveedor-email.md` con el formato exacto.
4. Añade la fila en `docs/adr/README.md`.
5. Confirma con resumen.
