# ADR-0002 — Modelo de datos: tags como entidad de primera clase y ritmos `{tipo, valor}`

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (modelo de grupos), `research/findings.md` (card-sort RG/VG), `risks.md` (R3b cerrado, R16), ADR-0004 (base de datos), ADR-0007 (monolito modular), ADR-0008 (arquitectura hexagonal y DDD)

## Contexto y problema

El card-sort con RG y VG (ver `research/findings.md`) **refutó la taxonomía rígida** nivel × distancia × carrera. La decisión cerrada en `vision.md` es: los grupos se forman con **tags libres** definidos por cada club, y un grupo es una **consulta nombrada sobre tags**.

Por otro lado, `vision.md` fija que los ritmos del plan de entrenamiento deben poder expresarse de forma relativa (% de umbral, % de marca) aunque la UI del MVP solo permita ritmos absolutos (hipótesis H5).

Ambas decisiones son de **modelado de datos** y tienen que estar fijadas antes de la primera migración de esquema, porque retrofitearlas es caro. Este ADR las formaliza.

## Drivers de la decisión

- El modelo debe soportar que **cada club invente su propia taxonomía** (keys y valores).
- El catálogo de keys y de valores debe ser una **entidad gestionable** — el editor de taxonomía (wireframe/spec 02) tiene que poder listarlo, renombrarlo y borrarlo.
- Un grupo no es una lista estática de alumnos: es una **consulta** que se recalcula.
- Hay que permitir **excepciones manuales** de pertenencia (M7) que prevalecen sobre la consulta.
- Hay que poder añadir el feature "ritmos relativos a marcas" (H5) **sin migración** de datos.
- Evitar columnas *hardcodeadas* que aten el modelo a la taxonomía de hoy, y evitar datos sucios por falta de catálogo.

## Opciones consideradas

- **Opción A** — Tags como entidad de primera clase (catálogo de dos niveles `TagKey` / `TagValue`, relación N-M con alumno); grupo = conjunto de tags requeridos.
- **Opción B** — Columnas fijas por eje (nivel, distancia, carrera) en la tabla de alumnos.
- **Opción C** — Un campo JSON libre de "atributos" por alumno, sin entidad Tag.

### Opción A — Tags entidad de primera clase

Catálogo de dos niveles: `TagKey` (los ejes de la taxonomía del club) y `TagValue` (los valores de cada eje). Relación N-M `alumno_tag`. El grupo es un `{nombre}` más un conjunto de `TagValue` requeridos. Excepciones manuales como tabla aparte.

- 👍 Cada club define su taxonomía sin tocar el esquema.
- 👍 Catálogo limpio de keys y valores → el editor de taxonomía mapea 1:1 a entidades.
- 👍 La pertenencia a un grupo es un dato, no código — se edita en runtime.
- 👍 Permite añadir keys nuevas (terreno, estado, día de entreno…) sin migración.
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

**Opción A: tags como entidad de primera clase (catálogo de dos niveles), grupo como conjunto de tags requeridos.**

### Tags — catálogo de dos niveles

- **`TagKey`** — `{id, club_id, nombre, …}`. Los ejes de la taxonomía del club: `nivel`, `objetivo`, `terreno`, `día de entreno`… El conjunto de `TagKey` de un club **es** su taxonomía.
- **`TagValue`** — `{id, tag_key_id, valor, metadata?}`. Los valores permitidos de cada eje. `metadata` es una columna **`JSONB`** opcional que solo usan los tipos que la necesitan: p. ej. una carrera de la key `objetivo` guarda `{fecha, distancia}`; un valor de `nivel` la deja vacía.
- **`alumno_tag`** — `{alumno_id, tag_value_id}`, relación N-M. Un alumno puede tener varios valores de la misma key.

Se descartó un `Tag` plano de una sola tabla (con `key` como columna de texto): sin entidad de catálogo reaparecería el problema de datos sucios (`nivel` / `Nivel`) que descarta la Opción C, y el editor de taxonomía no tendría una entidad real sobre la que operar. El editor de taxonomía (spec 02) trabaja directamente sobre `TagKey` y `TagValue`.

### Unicidad de la taxonomía

Un club no debe poder acumular keys ni valores duplicados. La unicidad se garantiza en **tres capas**:

- **Restricción en BD** — índice único por club, **insensible a mayúsculas, espacios y acentos**: `UNIQUE (club_id, unaccent(lower(trim(nombre))))` en `TagKey` y `UNIQUE (tag_key_id, unaccent(lower(trim(valor))))` en `TagValue`. Así `"Nivel"`, `"nivel "` y `"Nível"` cuentan como la misma. Se **guarda** el `nombre` tal y como lo tecleó el admin (forma de visualización); la normalización solo se aplica a la comprobación de unicidad.
- **Invariante del agregado** — la taxonomía la posee un agregado `Taxonomía` (módulo Club y taxonomía); su raíz rechaza un nombre duplicado **antes** de persistir y devuelve un error de dominio (`EtiquetaDuplicada`). El índice único de BD queda como **red de seguridad** ante condiciones de carrera.
- **UX del editor** (spec 02) — muestra las keys y valores existentes y, al teclear uno nuevo, ofrece reutilizar la coincidencia en lugar de crear un duplicado.

Nota de implementación: la función `unaccent()` de PostgreSQL es `STABLE`, no `IMMUTABLE`; para usarla en un índice hay que envolverla en una función `IMMUTABLE` propia, y la extensión `unaccent` debe estar habilitada en la base de datos.

### Grupos — conjunto de tags requeridos

- **`Grupo`** — `{id, club_id, nombre, entrenadores[]}`.
- **`grupo_tag_requerido`** — `{grupo_id, tag_value_id}`. La "consulta" del grupo **es** este conjunto de filas: un alumno pertenece al grupo si tiene **todos** los `tag_value_id` requeridos. Solo `AND` en el MVP.
- Resolver un grupo es SQL relacional puro sobre `alumno_tag` ⋈ `grupo_tag_requerido`: indexable, y un conjunto de filas **no puede estar mal formado** — desaparece el riesgo de "queries de grupo corruptas".
- **Limitación conocida del MVP**: con solo `AND`, un grupo no puede expresar disyunciones del tipo *"nivel medio **o** avanzado"*. El `OR`/`NOT` es evolución prevista (ver Notas).
- **Excepciones manuales (M7)** — tabla `grupo_alumno_override(grupo_id, alumno_id, incluido: bool)` que prevalece sobre el conjunto requerido.

### Snapshot al publicar plan

Al publicar el plan semanal a un grupo, se congela la lista de alumnos resueltos en ese momento; cambios posteriores de tags no alteran el plan ya publicado.

### Ritmos — `{tipo, valor}` conceptual, columnas tipadas en persistencia

Conceptualmente, toda intensidad de una sesión es un par `{tipo, valor}`:

- `absoluto` — ritmo en min/km.
- `pct_umbral` — % del umbral del corredor.
- `pct_marca` — % de la marca personal a una distancia estándar.

`Ritmo` es un **value object** (ADR-0008) embebido en `Sesión` como **columnas tipadas**, no como JSON:

- `ritmo_tipo` — enum (`absoluto` | `pct_umbral` | `pct_marca`).
- `ritmo_valor` — **numérico y uniforme** en los tres tipos. Para `absoluto` se guarda en **segundos por kilómetro enteros** (p. ej. `210` = 3:30 min/km); la UI lo formatea a `"3:30"` al mostrarlo. Para los `pct_*`, el porcentaje.
- `ritmo_distancia` — enum nullable de distancia estándar de marca (`5k` | `10k` | `21k` | `42k`); solo se usa con `pct_marca`.

La UI del MVP solo crea ritmos `absoluto`; las columnas de los otros tipos existen desde la primera migración, para poder añadir H5 sin migración de datos.

### Reglas de oro para el equipo

- Toda lógica de agrupación se hace sobre tags; **ninguna columna *hardcodea*** un eje de la taxonomía.
- Todo ritmo se guarda como `Ritmo` tipado (`tipo` + `valor` numérico), **nunca como un string suelto**.
- El `metadata JSONB` solo carga el apéndice variable de un `TagValue`; el modelo estructural es siempre relacional.

### Ubicación en módulos (ADR-0007 / ADR-0004)

- `TagKey`, `TagValue`, `alumno_tag`, `Alumno`, `Grupo`, `grupo_tag_requerido` y `grupo_alumno_override` viven en el módulo **Club y taxonomía** (schema `club_taxonomia`).
- `Ritmo` (embebido en `Sesión`) y el snapshot de publicación viven en el módulo **Planificación** (schema `planificacion`).
- Las referencias entre módulos —el snapshot de Planificación apunta a alumnos de Club y taxonomía— son por **ID suelto, sin FK cruzada** (ADR-0004).

## Consecuencias

### Positivas

- Cada club configura su taxonomía sin intervención de desarrollo (cierra R3b).
- Catálogo limpio de keys y valores → el editor de taxonomía mapea 1:1 a entidades; renombrar una key es una sola fila.
- La pertenencia a grupo es un conjunto relacional indexable que **no puede corromperse**.
- Añadir el feature "ritmos relativos por marcas" (H5) será UI + cálculo, sin migración.
- Ritmos numéricos uniformes → ordenar, validar y calcular sin parsear strings.
- El paso futuro a tags 100% libres ya está soportado por el esquema.

### Negativas / coste asumido

- Un nivel más de indirección (`TagKey` → `TagValue` → `alumno_tag`) que las columnas fijas; alguna consulta con un `JOIN` extra.
- Las consultas de pertenencia a grupo son más complejas que un `WHERE nivel = 'medio'`. Hay que diseñar índices con cuidado (se concreta en ADR-0004).
- `AND`-only: un grupo no puede expresar disyunciones; aceptado para el MVP.
- El equipo debe entender el modelo tag/grupo antes de tocar nada — se documenta y se incluye en el onboarding técnico.

### Riesgos y mitigaciones

- **Rendimiento de la resolución de grupos a ~500 alumnos** (R16) → índices sobre `alumno_tag(tag_value_id, alumno_id)` y sobre `grupo_tag_requerido`; resolver la pertenencia con SQL indexado, no en memoria; medir con datos del club piloto.
- **Datos sucios en valores de tag** → el catálogo `TagValue` y el constructor visual (wireframe 04) solo permiten elegir valores existentes; no se teclea texto libre al asignar tags a un alumno.

## Notas

- El `OR` / `NOT` en las queries de grupo es una **evolución prevista, no MVP**: requerirá pasar de `grupo_tag_requerido` a un modelo de expresión y una migración acotada.
- La generalización multi-club ya está soportada por el esquema: `club_id` está en todas las tablas desde el día 1 (ADR-0006).
- Revisar el modelo de snapshot si los entrenadores piden que el plan publicado "siga vivo" ante cambios de tags — hoy se asume congelado.
- Si las carreras ganan features propias (resultados, inscripciones, dorsales), se evaluará promover el `TagValue` de carrera a una entidad `Carrera` tipada — ADR futuro, hoy innecesario.
- Si en el futuro las `TagKey` / `TagValue` se pueden archivar, la unicidad pasará a un índice único **parcial** (solo sobre las activas). Hoy no se contempla el archivado de tags.
