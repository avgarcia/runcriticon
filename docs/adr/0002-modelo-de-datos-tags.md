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

### Ritmos — `Absoluto` o `Relativo` a una marca

> **Cambio respecto a la versión inicial de este ADR**: la H5 (ritmos relativos) pasa de COULD a MUST del MVP. El modelo de `Ritmo` se simplifica para reflejar cómo lo piensan los entrenadores en la práctica: *"ritmo de 10K + 10s/km"*, *"maratón − 5s/km"*. Se descarta el `pct_umbral` y el `pct_marca` originales: % no es la forma natural de expresarlo en running, y el umbral añadiría una variable que el MVP no necesita.

Toda intensidad de una sesión es un `Ritmo`, **value object** (ADR-0008) embebido en `Sesión` como columnas tipadas, no como JSON. Dos variantes:

- **`absoluto`** — `mm:ss/km` concreto. Todos los alumnos del grupo ven el mismo número.
- **`relativo`** — *delta sobre una marca estándar del corredor*. El entrenador especifica `{referencia, delta}`; cada alumno ve su valor absoluto calculado a partir de **su** marca.

Columnas en `planificacion.sesion`:

| Columna | Tipo | Cuándo aplica |
|---|---|---|
| `ritmo_tipo` | enum `ABSOLUTO` \| `RELATIVO` | siempre |
| `ritmo_seg_por_km` | `INT` nullable | solo si `ABSOLUTO` — segundos por kilómetro (p. ej. `210` = 3:30/km) |
| `ritmo_ref_distancia` | enum nullable `5K` \| `10K` \| `21K` \| `42K` | solo si `RELATIVO` |
| `ritmo_delta_seg_por_km` | `INT` nullable, **firmado** | solo si `RELATIVO` — positivo = más lento que la marca; negativo = más rápido |

Reglas de coherencia (constraints o invariantes del agregado):

- Si `ritmo_tipo = ABSOLUTO` → `ritmo_seg_por_km` no nulo, las dos columnas de referencia nulas.
- Si `ritmo_tipo = RELATIVO` → `ritmo_seg_por_km` nulo, las dos columnas de referencia no nulas.

### Marcas del corredor — entidad nueva en Seguimiento

Las **marcas** son del alumno y nadie más del club las ve.

`seguimiento.marca_alumno`:

| Columna | Tipo |
|---|---|
| `alumno_id` | `UUID NOT NULL` |
| `distancia` | enum `5K` \| `10K` \| `21K` \| `42K` |
| `tiempo_segundos` | `INT NOT NULL CHECK (> 0)` |
| `modificado_en` | `TIMESTAMPTZ` |
| **PK** | `(alumno_id, distancia)` |

Sin histórico en MVP: el alumno corre una mejor marca, la actualiza, sobreescribe la anterior.

**Privacidad fuerte**: solo el alumno lee y escribe sus marcas. El entrenador y el admin **no** ven valores ni siquiera contadores agregados ("X alumnos sin marca de 10K"). El entrenador solo conoce **qué referencia pidió** en cada sesión; la marca concreta de cada alumno es invisible para él.

### Resolución de ritmos en Seguimiento

El read model `seguimiento.plan_resuelto_por_alumno` (introducido por la M12) se enriquece para resolver el ritmo:

| Columna | Significado |
|---|---|
| `ritmo_tipo_origen` | `ABSOLUTO` o `RELATIVO` — lo que pidió el entrenador. |
| `ritmo_calculado_seg_por_km` | nullable. Si el origen es `ABSOLUTO`, copia el valor. Si es `RELATIVO` y el alumno **tiene** la marca de referencia, calcula `marca.ritmo + delta`. Si no la tiene, queda `NULL`. |
| `ritmo_falta_marca` | nullable. La distancia que el alumno necesita rellenar para resolver (`10K`, `42K`…). El frontend del alumno usa este campo para mostrar el CTA *"Añade tu marca de 10K"*. |
| `ritmo_referencia_distancia` | nullable. Solo para mostrar al alumno como contexto sutil (*"basado en tu 10K"*); no se enseña al entrenador. |

Eventos que disparan el recálculo (consumidos dentro del propio módulo Seguimiento):

- `MarcaActualizada(alumnoId, distancia, tiempoSegundos)` — el alumno modificó su marca. Se recalculan las filas del read model donde `ritmo_referencia_distancia = distancia` y `alumno_id = ese alumno`.
- `PlanPublicado` y `SesionPersonalizada` — siguen siendo los de M12; al consumirlos se rellena `ritmo_calculado_seg_por_km` con la marca actual del alumno.

La UI del MVP **ya soporta ambos tipos** desde el día 1 (entrenador elige el tipo en el editor de sesión; alumno gestiona sus marcas desde su pantalla). La hipótesis H5 deja de ser hipótesis y se valida con el club piloto.

### Reglas de oro para el equipo

- Toda lógica de agrupación se hace sobre tags; **ninguna columna *hardcodea*** un eje de la taxonomía.
- Todo ritmo se guarda como `Ritmo` tipado (`tipo` + `valor` numérico), **nunca como un string suelto**.
- El `metadata JSONB` solo carga el apéndice variable de un `TagValue`; el modelo estructural es siempre relacional.

### Ubicación en módulos (ADR-0007 / ADR-0004)

- `TagKey`, `TagValue`, `alumno_tag`, `Alumno`, `Grupo`, `grupo_tag_requerido` y `grupo_alumno_override` viven en el módulo **Club y taxonomía** (schema `club_taxonomia`).
- `Ritmo` (embebido en `Sesión`), `Personalización` y el snapshot de publicación viven en el módulo **Planificación** (schema `planificacion`).
- `marca_alumno` y `plan_resuelto_por_alumno` viven en el módulo **Seguimiento** (schema `seguimiento`). Las marcas las gestiona solo el alumno (privacidad fuerte); el read model las consume para resolver los ritmos relativos.
- Las referencias entre módulos —el snapshot de Planificación apunta a alumnos de Club y taxonomía; el read model de Seguimiento apunta a planes y alumnos— son por **ID suelto, sin FK cruzada** (ADR-0004).

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
