# ADR-0002 — Modelo de datos: tags como entidad de primera clase y ritmos `{tipo, valor}`

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (modelo de grupos), `research/findings.md` (card-sort RG/VG), `risks.md` (R3b cerrado, R16), ADR-0004 (base de datos)

## Contexto y problema

El card-sort con RG y VG (ver `research/findings.md`) **refutó la taxonomía rígida** nivel × distancia × carrera. La decisión cerrada en `vision.md` es: los grupos se forman con **tags libres** definidos por cada club, y un grupo es una **consulta nombrada sobre tags**.

Por otro lado, `vision.md` fija que los ritmos del plan de entrenamiento deben poder expresarse de forma relativa (% de umbral, % de marca) aunque la UI del MVP solo permita ritmos absolutos (hipótesis H5).

Ambas decisiones son de **modelado de datos** y tienen que estar fijadas antes de la primera migración de esquema, porque retrofitearlas es caro. Este ADR las formaliza.

## Drivers de la decisión

- El modelo debe soportar que **cada club invente su propia taxonomía** (tags y valores).
- Un grupo no es una lista estática de alumnos: es una **consulta** que se recalcula.
- Hay que permitir **excepciones manuales** de pertenencia (M7) que prevalecen sobre la consulta.
- Hay que poder añadir el feature "ritmos relativos a marcas" (H5) **sin migración** de datos.
- Evitar columnas *hardcodeadas* que aten el modelo a la taxonomía de hoy.

## Opciones consideradas

- **Opción A** — Tags como entidad de primera clase (`Tag`, relación N-M con alumno); grupo = `{nombre, query}`.
- **Opción B** — Columnas fijas por eje (nivel, distancia, carrera) en la tabla de alumnos.
- **Opción C** — Un campo JSON libre de "atributos" por alumno, sin entidad Tag.

### Opción A — Tags entidad de primera clase

`Tag(id, club_id, key, value, metadata)`. Relación N-M `alumno_tag`. `Grupo(id, club_id, nombre, query, ...)` donde `query` es una expresión booleana sobre tags. Excepciones manuales como tabla aparte (`grupo_alumno_override`).

- 👍 Cada club define su taxonomía sin tocar el esquema.
- 👍 La consulta del grupo es un dato, no código — se edita en runtime.
- 👍 Permite añadir tags nuevos (terreno, estado, día de entreno…) sin migración.
- 👎 Más complejo de consultar que columnas fijas; requiere pensar bien los índices.

### Opción B — Columnas fijas por eje

`alumno(nivel, distancia, carrera_id, ...)`.

- 👍 Consultas triviales y rápidas.
- 👎 **Es exactamente la taxonomía rígida que el card-sort refutó.** Cada eje nuevo es una migración. No sirve.

### Opción C — Campo JSON de atributos libres

`alumno(atributos JSONB)` sin entidad Tag.

- 👍 Flexible, sin migraciones para nuevos atributos.
- 👎 No hay catálogo de valores válidos → datos sucios ("medio" vs "Medio").
- 👎 La metadata de los valores (fecha y distancia de una carrera) no tiene dónde vivir de forma estructurada.
- 👎 Difícil construir el editor de taxonomía (spec 02) sin una entidad Tag real.

## Decisión

**Opción A: tags como entidad de primera clase, grupo como consulta nombrada sobre tags.**

Modelo conceptual del MVP:

- **`Tag`** — `{id, club_id, key, value, metadata?}`. Ejemplos: `(nivel, medio)`, `(objetivo, "Maratón Valencia", {fecha, distancia})`. El conjunto de `key` distintos es la taxonomía del club; el catálogo de carreras es el conjunto de valores de la `key` `objetivo`.
- **`Alumno ⇄ Tag`** — relación N-M. Un alumno puede tener varios valores de la misma `key`.
- **`Grupo`** — `{id, club_id, nombre, query, entrenadores[]}`. `query` es una expresión booleana sobre tags (solo `AND` en MVP; `OR` queda fuera).
- **Excepciones manuales (M7)** — tabla `grupo_alumno_override(grupo_id, alumno_id, incluido: bool)` que prevalece sobre la `query`.
- **Snapshot al publicar plan** — al publicar el plan semanal a un grupo, se congela la lista de alumnos resueltos en ese momento; cambios posteriores de tags no alteran el plan ya publicado.

**Ritmos del plan** — toda intensidad de una sesión se modela como `{tipo, valor}`:

- `{tipo: "absoluto", valor: "3:30"}` — ritmo en min/km.
- `{tipo: "pct_umbral", valor: 95}` — % del umbral del corredor.
- `{tipo: "pct_marca", valor: 97, distancia: "10k"}` — % de la marca personal.

La UI del MVP solo crea ritmos `absoluto`; el modelo admite los otros desde el día 1.

**Regla de oro para el equipo**: toda lógica de agrupación se hace sobre tags; ninguna columna *hardcodea* un eje de la taxonomía. Todo ritmo se guarda como `{tipo, valor}`, nunca como un string suelto.

## Consecuencias

### Positivas

- Cada club configura su taxonomía sin intervención de desarrollo (cierra R3b).
- Añadir el feature "ritmos relativos por marcas" (H5) será UI + cálculo, sin migración.
- El paso futuro a tags 100% libres ya está soportado por el esquema.

### Negativas / coste asumido

- Las consultas de pertenencia a grupo son más complejas que un `WHERE nivel = 'medio'`. Hay que diseñar índices con cuidado (se concreta en ADR-0004).
- El equipo debe entender el modelo tag/query antes de tocar nada — se documenta y se incluye en el onboarding técnico.

### Riesgos y mitigaciones

- **Rendimiento de la resolución de grupos a 500 alumnos** (R16) → resolver la `query` con SQL indexado, no en memoria; medir con datos del club piloto.
- **Queries de grupo mal formadas** → validación en el backend; el constructor visual (wireframe 04) no permite construir queries inválidas.

## Notas

- El `OR` en las queries de grupo y la generalización multi-club (`club_id` ya está en todas las tablas) son evoluciones previstas, no MVP.
- Revisar el modelo de snapshot si los entrenadores piden que el plan publicado "siga vivo" ante cambios de tags — hoy se asume congelado.
