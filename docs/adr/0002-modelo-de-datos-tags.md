# ADR-0002 — Modelo de datos del dominio: tags, ritmos relativos y marcas privadas

- **Estado**: Propuesto
- **Fecha**: 2026-05-20 · revisado 2026-05-27 (cambio del modelo de Ritmo a `Absoluto | Relativo`, marcas como entidad privada del alumno en Seguimiento; reorganización Nivel 1: premisas heredadas + índice + numeración de sub-decisiones)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (modelo de grupos), `research/findings.md` (card-sort RG/VG, ronda 2 informal), `risks.md` (R3b cerrado, R15 cerrado, R16), ADR-0004 (base de datos), ADR-0006 (mono-tenant), ADR-0007 (monolito modular), ADR-0008 (arquitectura hexagonal y DDD)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre el modelo del dominio. Las nueve sub-decisiones cubren cuatro áreas:

- **Tags y grupos (D1-D5)** — cómo se modela la taxonomía del club y la pertenencia a grupo, incluida la congelación de membresía al publicar.
- **Ritmos (D6)** — cómo se expresa la intensidad de una sesión.
- **Marcas y resolución (D7-D8)** — cómo se calcula y almacena el ritmo absoluto que ve cada alumno.
- **Personalización del plan por alumno (D9)** — la excepción explícita que sostiene el modelo plan-por-grupo.

| #  | Sub-decisión                                                                          | Capa         |
|----|----------------------------------------------------------------------------------------|--------------|
| D1 | [Tags como entidad de primera clase (catálogo de dos niveles)](#d1)                    | Estratégica  |
| D2 | [Unicidad de la taxonomía en tres capas](#d2)                                          | Operativa    |
| D3 | [Grupos como conjunto de tags requeridos (AND-only en MVP)](#d3)                       | Estratégica  |
| D4 | [Override de grupo: excepción manual de pertenencia](#d4)                              | Operativa    |
| D5 | [Snapshot al publicar plan: congelación de membresía](#d5)                             | Operativa    |
| D6 | [Ritmos: `Absoluto` o `Relativo` a una marca](#d6)                                     | Estratégica  |
| D7 | [Marcas del corredor: entidad privada en Seguimiento](#d7)                             | Estratégica  |
| D8 | [Resolución de ritmos en read model de Seguimiento](#d8)                               | Operativa    |
| D9 | [Personalización: entidad hija de `PlanSemanal`](#d9)                                  | Estratégica  |

## Contexto y problema

El card-sort con RG y VG (ver `research/findings.md`) **refutó la taxonomía rígida** nivel × distancia × carrera. La decisión cerrada en `vision.md` es: los grupos se forman con **tags libres** definidos por cada club, y un grupo es una **consulta nombrada sobre tags**.

Por otro lado, `vision.md` fija que los ritmos del plan de entrenamiento deben poder expresarse de forma relativa a la marca del corredor (delta sobre 5K/10K/21K/42K), aunque la UI del MVP del plan se diseñe pensando primero en la usabilidad del entrenador.

Ambas decisiones son de **modelado de datos** y tienen que estar fijadas antes de la primera migración de esquema, porque retrofitearlas es caro. Este ADR las formaliza.

## Requisitos no funcionales (dimensionado de datos)

Estos límites son **del modelo**, no de la carga. Definen hasta dónde aguanta el esquema sin replantearse — la carga concurrente, la latencia y la infra siguen rigiendo la vida operativa diaria y se fijan en ADR-0001. Aquí dimensionamos con holgura porque el modelo relacional con índices apropiados absorbe órdenes de magnitud más datos sin coste extra; mantenerlo estrecho ahorraría poco y crearía deuda en cuanto el club piloto crezca o entren más clubes.

| Dimensión | Valor |
|---|---|
| **Alumnos por club** | Hasta **5.000** (sobra para clubes amateur grandes; los mayores españoles rondan 1.500-2.000) |
| **Tags por club** (`TagKey`) | Hasta **30** (deja margen para tags muy específicos del club: día de WhatsApp, tipo de cuota, etc.) |
| **Valores por tag** (`TagValue`) | Hasta **100** (el tag `objetivo` con catálogo de carreras es el más denso) |
| **Grupos por club** (`Grupo`) | Hasta **200** (activos + archivados a lo largo del año) |
| **Filas en `alumno_tag`** | Hasta **250.000** (5.000 alumnos × ~50 tags promedio) |
| **Filas en `marca_alumno`** | Hasta **20.000** (5.000 alumnos × 4 distancias estándar) |
| **Filas en `personalizacion`** (anual) | Hasta **100.000** (50 grupos × 5 sesiones × 50 semanas × ~10 personalizaciones/grupo) |
| **Filas en `plan_resuelto_por_alumno`** (anual) | Hasta **1.500.000** (5.000 alumnos × 7 días × ~50 semanas). El read model más grande; con índice sobre `(alumno_id, dia)` PostgreSQL lo aguanta sin esfuerzo |

A estos volúmenes, los patrones de consulta de este ADR (la membresía de grupo en D3, la resolución de ritmo en D8, la lectura de la vista "hoy" del alumno) se mantienen por debajo del **p95 < 400 ms** fijado por ADR-0001 con índices bien planteados y sin caché aplicativa. La frontera de revisar el modelo no es por tamaño de tablas — es por nueva semántica (multi-club real, OR/NOT en queries de grupo, histórico de marcas).

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR** — se asumen y condicionan toda la decisión que sigue. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **El card-sort con RG y VG (mayo 2026) ya refutó la taxonomía rígida** nivel × distancia × carrera. La decisión de usar tags libres como modelo, en lugar de columnas fijas o estructuras taxonómicas duras, viene de evidencia del piloto. Si en el futuro otro club piloto rebate la conclusión, este ADR se reabre.
- **PostgreSQL como base de datos** (ADR-0004). El modelo asume relacional clásico con índices y la extensión `unaccent`. Una BD distinta (NoSQL, multi-modelo) invalidaría buena parte de las decisiones aquí tomadas.
- **Arquitectura hexagonal con dominio puro** (ADR-0008). Los conceptos del modelo se expresan como *value objects* y *agregados* en la capa `domain`, con persistencia separada en `infrastructure` mediante mapeador. No se mezcla aquí qué pertenece a JPA.
- **Privacidad del alumno como invariante** (consolidada 2026-05-27 tras ronda 2 informal con RG/VG). Las marcas del corredor son privadas: las gestiona solo el alumno y nadie más del club las ve, ni siquiera como contador agregado. Esta premisa condiciona la ubicación de `MarcaAlumno` (D7) en el módulo Seguimiento y bloquea cualquier vista de marcas en pantallas de entrenador/admin.
- **Mono-tenant en MVP** (ADR-0006). El esquema lleva `club_id` desde el día 1 como **preparación**, pero las queries del MVP son siempre de un único club. La generalización a multi-tenant es otra decisión y otro ADR cuando llegue.

## Drivers de la decisión

- El modelo debe soportar que **cada club invente su propia taxonomía** (keys y valores).
- El catálogo de keys y de valores debe ser una **entidad gestionable** — el editor de taxonomía (wireframe/spec 02) tiene que poder listarlo, renombrarlo y borrarlo.
- Un grupo no es una lista estática de alumnos: es una **consulta** que se recalcula.
- Hay que permitir **excepciones manuales** de pertenencia (M7) que prevalecen sobre la consulta.
- Hay que poder expresar **ritmos relativos a las marcas del corredor** desde el primer día (M19 + M20 del backlog).
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

Las nueve sub-decisiones desarrolladas a continuación. Cinco son **estratégicas** (D1, D3, D6, D7, D9 — tag como entidad, grupo como query, ritmo dual, marca privada, personalización como entidad hija); el resto son **operativas** (D2, D4, D5, D8 — unicidad, override, snapshot, read model) y se derivan de las anteriores.

<a id="d1"></a>
### D1 — Tags como entidad de primera clase (catálogo de dos niveles)

- **`TagKey`** — `{id, club_id, nombre, …}`. Los ejes de la taxonomía del club: `nivel`, `objetivo`, `terreno`, `día de entreno`… El conjunto de `TagKey` de un club **es** su taxonomía.
- **`TagValue`** — `{id, tag_key_id, valor, metadata?}`. Los valores permitidos de cada eje. `metadata` es una columna **`JSONB`** opcional que solo usan los tipos que la necesitan: p. ej. una carrera de la key `objetivo` guarda `{fecha, distancia}`; un valor de `nivel` la deja vacía.
- **`alumno_tag`** — `{alumno_id, tag_value_id}`, relación N-M. Un alumno puede tener varios valores de la misma key.

Se descartó un `Tag` plano de una sola tabla (con `key` como columna de texto): sin entidad de catálogo reaparecería el problema de datos sucios (`nivel` / `Nivel`) que descarta la Opción C, y el editor de taxonomía no tendría una entidad real sobre la que operar. El editor de taxonomía (spec 02) trabaja directamente sobre `TagKey` y `TagValue`.

#### `metadata` como estructura tipada en el dominio, JSONB como persistencia

El campo `metadata` no se trabaja como JSON genérico en el dominio. Se modela como **sealed class de Kotlin** en `domain`, con una variante por tipo de metadata que necesite cada `TagKey`. El JSONB es **detalle de persistencia** que solo conoce la capa `infrastructure`:

```kotlin
// domain — value object tipado
sealed class TagValueMetadata {
    object Vacia : TagValueMetadata()                                    // nivel, terreno, estado…
    data class Carrera(val fecha: LocalDate, val distancia: Distancia)   // objetivo
        : TagValueMetadata()
    // futuras variantes se añaden aquí
}
```

Implicaciones:

- **Imposible construir un `TagValueMetadata` inválido**: el sistema de tipos de Kotlin garantiza que una `Carrera` siempre tiene `fecha` y `distancia` válidas. El agregado `Taxonomía` no necesita validar JSON contra ningún schema externo.
- **Cero dependencias nuevas**: no se introduce librería de JSON Schema. Coherente con ADR-0008 (dominio puro).
- **`Distancia` es el value object compartido** (ver Reglas de oro) — se reutiliza aquí, no se duplica.
- **El mapeador (`infrastructure`)** serializa la variante `Carrera` a `{"tipo":"carrera","fecha":"2026-12-06","distancia":"42K"}` y deserializa al tipo concreto en el camino inverso. Si la deserialización falla por un JSON corrompido (migración manual mal hecha, bug histórico), el mapeador devuelve `Vacia` y **emite un log de error con `traceId`** — degradación grácil + alerta operativa, nunca propagar el corruption al dominio.
- **El frontend recibe el tipo via OpenAPI contract-first** (ADR-0001 D10): no duplica la definición ni hace su propia validación de JSON, lee el tipo de la spec generada.

Se rechazó usar **JSON Schema versionado** como alternativa: en MVP solo hay un tipo de metadata (`Carrera`), y la disciplina la entrega el sistema de tipos sin librería extra. Si en el futuro aparecen 5+ variantes con formas complejas, se reevalúa.

<a id="d2"></a>
### D2 — Unicidad de la taxonomía en tres capas

Un club no debe poder acumular keys ni valores duplicados. La unicidad se garantiza en **tres capas**:

- **Restricción en BD** — índice único por club, **insensible a mayúsculas, espacios y acentos**: `UNIQUE (club_id, unaccent(lower(trim(nombre))))` en `TagKey` y `UNIQUE (tag_key_id, unaccent(lower(trim(valor))))` en `TagValue`. Así `"Nivel"`, `"nivel "` y `"Nível"` cuentan como la misma. Se **guarda** el `nombre` tal y como lo tecleó el admin (forma de visualización); la normalización solo se aplica a la comprobación de unicidad.
- **Invariante del agregado** — la taxonomía la posee un agregado `Taxonomía` (módulo Club y taxonomía); su raíz rechaza un nombre duplicado **antes** de persistir y devuelve un error de dominio (`EtiquetaDuplicada`). El índice único de BD queda como **red de seguridad** ante condiciones de carrera.
- **UX del editor** (spec 02) — muestra las keys y valores existentes y, al teclear uno nuevo, ofrece reutilizar la coincidencia en lugar de crear un duplicado.

Nota de implementación: la función `unaccent()` de PostgreSQL es `STABLE`, no `IMMUTABLE`; para usarla en un índice hay que envolverla en una función `IMMUTABLE` propia, y la extensión `unaccent` debe estar habilitada en la base de datos.

<a id="d3"></a>
### D3 — Grupos como conjunto de tags requeridos (AND-only en MVP)

- **`Grupo`** — `{id, club_id, nombre, entrenadores[]}`.
- **`grupo_tag_requerido`** — `{grupo_id, tag_value_id}`. La "consulta" del grupo **es** este conjunto de filas: un alumno pertenece al grupo si tiene **todos** los `tag_value_id` requeridos. Solo `AND` en el MVP.
- Resolver un grupo es SQL relacional puro sobre `alumno_tag` ⋈ `grupo_tag_requerido`: indexable, y un conjunto de filas **no puede estar mal formado** — desaparece el riesgo de "queries de grupo corruptas".
- **Limitación conocida del MVP**: con solo `AND`, un grupo no puede expresar disyunciones del tipo *"nivel medio **o** avanzado"*. El `OR`/`NOT` es evolución prevista (ver Notas).

**SQL canónico de resolución de membresía** (combinando D3 + D4). Lo dejamos escrito aquí para que el equipo no improvise el día 1; un `JOIN` ingenuo que cuente filas en lugar de tags distintos es un bug típico en este tipo de modelos.

```sql
-- Devuelve los alumnos que pertenecen efectivamente al grupo,
-- combinando las condiciones de tags (D3) con los overrides (D4).
-- :grupo_id es el parámetro de entrada (se referencia tres veces).

WITH cumplen_tags AS (
  SELECT at.alumno_id
  FROM club_taxonomia.alumno_tag at
  JOIN club_taxonomia.grupo_tag_requerido gtr
    ON at.tag_value_id = gtr.tag_value_id
  WHERE gtr.grupo_id = :grupo_id
  GROUP BY at.alumno_id
  HAVING COUNT(DISTINCT gtr.tag_value_id) = (
    SELECT COUNT(*)
    FROM club_taxonomia.grupo_tag_requerido
    WHERE grupo_id = :grupo_id
  )
),
incluidos AS (
  SELECT alumno_id FROM club_taxonomia.grupo_alumno_override
  WHERE grupo_id = :grupo_id AND incluido = TRUE
),
excluidos AS (
  SELECT alumno_id FROM club_taxonomia.grupo_alumno_override
  WHERE grupo_id = :grupo_id AND incluido = FALSE
)
SELECT alumno_id FROM cumplen_tags
UNION
SELECT alumno_id FROM incluidos
EXCEPT
SELECT alumno_id FROM excluidos;
```

Notas del patrón:

- **`COUNT(DISTINCT gtr.tag_value_id)` en lugar de `COUNT(*)`** evita falsos positivos si un alumno tuviese filas repetidas en `alumno_tag` (defensa frente a datos sucios; el catálogo de D1 ya lo previene en aplicación, pero el SQL lo refuerza).
- **El subquery del `HAVING` compara con el número de tags requeridos del grupo**, no con un valor *hardcodeado*: si el grupo tiene 3 tags requeridos el alumno debe cumplir los 3; si tiene 5, los 5. El SQL es estable ante el alta/baja de condiciones del grupo.
- **`EXCEPT` final** materializa la prevalencia del override excluido sobre la unión de "cumple tags" + "incluido manual". Es exactamente la regla de D4.
- **Caso borde — grupo sin tags requeridos**: si `grupo_tag_requerido` está vacío para `:grupo_id`, `cumplen_tags` no devuelve filas (el JOIN no produce nada). El grupo lo forman **solo los incluidos manualmente**. Es la convención que asume la spec 04; si se cambia, este SQL cambia.
- **Índices imprescindibles** para que este patrón se mantenga sub-100ms a los volúmenes objetivo:
  - `alumno_tag (tag_value_id, alumno_id)` para el `JOIN` y el `GROUP BY`.
  - `grupo_tag_requerido (grupo_id, tag_value_id)` para el filtro `WHERE` y el `COUNT` del subquery.
  - `grupo_alumno_override (grupo_id, alumno_id, incluido)` para los dos CTEs de overrides.

<a id="d4"></a>
### D4 — Override de grupo: excepción manual de pertenencia

Tabla `grupo_alumno_override(grupo_id, alumno_id, incluido: bool)` que **prevalece sobre el conjunto requerido** de D3. La lógica de pertenencia efectiva es:

```
está_en_grupo(alumno) =
    (cumple_todos_los_tag_value_requeridos(alumno) AND NOT excluido_manualmente(alumno))
    OR incluido_manualmente(alumno)
```

Esta tabla materializa la MUST M7 ("ajuste manual de pertenencia") y absorbe casos reales que ningún filtro de tags cubrirá nunca (el alumno que el entrenador quiere meter aunque no encaje, o el que prefiere mantener al margen temporalmente). El UI del constructor de grupos (spec 04) muestra los overrides como sección separada bajo "Ajustes manuales".

<a id="d5"></a>
### D5 — Snapshot al publicar plan: congelación de membresía

Al publicar el plan semanal a un grupo, se **congela la lista de alumnos resueltos** en ese momento; cambios posteriores de tags o de overrides no alteran el plan ya publicado. El snapshot vive en el módulo Planificación (`schema planificacion`); su forma exacta de almacenamiento (tabla normalizada de pares `(plan_id, alumno_id)` con metadatos del momento) la fija la implementación del módulo respetando ADR-0004.

Este snapshot es lo que garantiza que un alumno al que el admin saca del grupo después de publicar siga viendo el plan de esa semana hasta el final, y que los reportes y personalizaciones referencien una lista estable.

<a id="d6"></a>
### D6 — Ritmos: `Absoluto` o `Relativo` a una marca

> **Cambio respecto a la versión inicial de este ADR**: la H5 (ritmos relativos) pasó de COULD a MUST del MVP en mayo de 2026. El modelo de `Ritmo` se simplificó para reflejar cómo lo piensan los entrenadores en la práctica: *"ritmo de 10K + 10s/km"*, *"maratón − 5s/km"*. Se descartó el `pct_umbral` y el `pct_marca` originales: % no es la forma natural de expresarlo en running, y el umbral añadiría una variable que el MVP no necesita.

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

<a id="d7"></a>
### D7 — Marcas del corredor: entidad privada en Seguimiento

Las **marcas** son del alumno y nadie más del club las ve. Esta privacidad fuerte está fijada por la premisa heredada correspondiente; la ubicación de la entidad en el módulo Seguimiento (no en Club y taxonomía) es la barrera arquitectónica que la sostiene.

`seguimiento.marca_alumno`:

| Columna | Tipo |
|---|---|
| `alumno_id` | `UUID NOT NULL` |
| `distancia` | enum `5K` \| `10K` \| `21K` \| `42K` |
| `tiempo_segundos` | `INT NOT NULL CHECK (> 0)` |
| `modificado_en` | `TIMESTAMPTZ` |
| **PK** | `(alumno_id, distancia)` |

Sin histórico en MVP: el alumno corre una mejor marca, la actualiza, sobreescribe la anterior. El histórico de PRs anteriores está en COULD del backlog.

**Privacidad fuerte**: solo el alumno lee y escribe sus marcas. El entrenador y el admin **no** ven valores ni siquiera contadores agregados ("X alumnos sin marca de 10K"). El entrenador solo conoce **qué referencia pidió** en cada sesión; la marca concreta de cada alumno es invisible para él.

**`MarcaAlumno` es un agregado pequeño** (DDD táctico, ADR-0008), no una fila suelta de persistencia. Su identidad es la PK compuesta `(alumnoId, distancia)`; su único invariante es `tiempoSegundos > 0`. No hay raíz "carpeta de marcas del alumno" que agrupe todas las distancias: cada `MarcaAlumno` es independiente, no se publica una marca como parte de un *batch*, y eso simplifica los casos de uso (cada actualización es atómica e independiente, sin invariantes cruzadas entre las cuatro distancias).

<a id="d8"></a>
### D8 — Resolución de ritmos en read model de Seguimiento

El read model `seguimiento.plan_resuelto_por_alumno` (introducido por la M12 — personalización) se enriquece para resolver el ritmo:

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

**Consumidores idempotentes y orden de eventos**. Los listeners de `MarcaActualizada`, `PlanPublicado` y `SesionPersonalizada` se diseñan **idempotentes** — coherente con ADR-0007 (events-first, los eventos pueden entregarse más de una vez). Concretamente, la actualización de `plan_resuelto_por_alumno` se hace con `INSERT … ON CONFLICT (alumno_id, plan_id, dia) DO UPDATE SET …` para que reprocesar un evento sea seguro. Las **race conditions** posibles (el alumno actualiza su marca al mismo tiempo que el entrenador publica un plan que la usa) se resuelven con el mismo patrón: el orden de llegada no importa, porque cada evento contiene la información suficiente para recalcular la fila final desde cero — el último evento en aplicarse fija el estado. No se requiere coordinación distribuida ni ordering cross-evento.

<a id="d9"></a>
### D9 — Personalización: entidad hija de `PlanSemanal`

La personalización (M12 del backlog) es la palanca que sostiene el modelo plan-por-grupo: el entrenador escribe **una** sesión que recibe todo el grupo y, donde haga falta, ajusta para un alumno concreto sin reescribir el plan. Sin esta sub-decisión, el modelo plan-por-grupo se rompe en cuanto un alumno se sale de la norma (lesión, viaje, vuelta de carga…). La citan literal de VG que da fuerza al diseño: *"si me obligas a separar por distancias voy a escribir 40 planes idénticos cambiando una línea"*.

`Personalizacion` es **entidad hija** del agregado `PlanSemanal` en el módulo Planificación, no una tabla suelta ni un patch lateral:

```
PlanSemanal (raíz del agregado)
  ├─ Sesion (entidad)            ← sesión base que ve todo el grupo
  └─ Personalizacion (entidad)
       ├─ alumnoId
       ├─ sesionId               ← apunta a la sesión sobrescrita
       ├─ override: Sesion       ← misma forma que Sesion, pero solo para ese alumno
       └─ mensajeAlAlumno: String?
```

Invariantes que protege la raíz:

- Una personalización **única** por `(plan, sesion, alumno)` — editar reemplaza, no acumula.
- El alumno debe estar en el snapshot publicado (D5) o, si el plan sigue en borrador, en el grupo en el momento de publicar.
- Resolver la sesión que ve un alumno es una **función pura** del agregado: `resolverSesionParaAlumno(plan, dia, alumno)` devuelve el override si existe, la sesión base si no.

**Persistencia** (schema `planificacion`):

```
planificacion.personalizacion (
  id,
  plan_id, sesion_id, alumno_id,
  override JSONB,            -- mismo shape que Sesion
  mensaje_al_alumno TEXT,    -- opcional, visible al alumno
  creado_en, modificado_en,
  UNIQUE (plan_id, sesion_id, alumno_id)
)
```

La tabla aparte (no JSONB embebido en `sesion`) habilita consultas tipo *"todas las personalizaciones de este alumno"*, *"todas las sesiones personalizadas de un plan"* y métricas futuras en la salud del club (cuántas personalizaciones de media aplica un entrenador, qué alumnos las concentran, etc.).

**Eventos** emitidos por Planificación (consumidos por Seguimiento para mantener el read model D8):

- `SesionPersonalizada(planId, sesionId, alumnoId, override, mensajeAlAlumno?)` — emitido al crear o editar.
- `PersonalizacionRetirada(planId, sesionId, alumnoId)` — emitido al quitar.

**Mensaje al alumno**: campo opcional de texto libre que el alumno verá junto a su sesión en la vista "hoy". Sustituye al "motivo" interno original; refleja la decisión consolidada en mayo 2026 de que el alumno **no** recibe ningún indicador de que su sesión esté personalizada — el mensaje, si lo hay, es la única señal explícita. El entrenador no envía notificación push; el alumno lo ve al refrescar su vista.

**Relación con D5 y D8**:

- Con **D5 (snapshot)**: una personalización solo es válida si el alumno está en el snapshot. Sacar al alumno del grupo después de publicar no invalida sus personalizaciones — el snapshot las preserva hasta el fin de la semana.
- Con **D8 (read model)**: el read model `plan_resuelto_por_alumno` consume `SesionPersonalizada` / `PersonalizacionRetirada` y guarda el override resuelto + el mensaje. La vista "hoy" del alumno lee de allí sin saber que existe la tabla `personalizacion`.

Detalle completo del flujo, eventos, casos borde (editar la base con personalizaciones vivas, alumno sacado del grupo, etc.) y *nota técnica de implementación*: ver `plan-implementacion-mvp.md`, sección "Nota técnica — la personalización (M12) es ciudadano de primera".

### Reglas de oro para el equipo

- Toda lógica de agrupación se hace sobre tags; **ninguna columna *hardcodea*** un eje de la taxonomía.
- Todo ritmo se guarda como `Ritmo` tipado (`Absoluto` o `Relativo` con sus columnas), **nunca como un string suelto**.
- El `metadata JSONB` solo carga el apéndice variable de un `TagValue`; el modelo estructural es siempre relacional. En el dominio se manipula como `TagValueMetadata` tipado (sealed class en `domain`, ver D1); **el JSONB nunca se toca como mapa genérico** — la serialización vive en el mapeador de `infrastructure`.
- **`Distancia` es un *value object* compartido, no un enum duplicado**: vive **una sola vez** en el dominio común y se reutiliza en `Ritmo.Relativo` (D6), `MarcaAlumno` (D7) y la metadata de `TagValue` cuando el tag es `objetivo` (D1). Si se amplían las distancias estándar (p. ej. añadir `1500m` o `ultra`), se amplía en ese único sitio; el equipo es responsable de **verificar en CI** que las tres referencias siguen siendo coherentes (un test de arquitectura ArchUnit que falle si alguien declara otra enum/constante de distancias en cualquier módulo).

### Estrategia de tests críticos del modelo

Los tipos de test los fija **ADR-0010** (pirámide: unitarios + integración con Testcontainers + contrato API + ArchUnit + fronteras de Modulith). Esta sección no los redefine — sólo señala qué **casos** del modelo de este ADR son los que duelen si fallan en producción. Si CI verde no cubre estos casos concretos, el ADR-0002 no se considera implementado.

| Ámbito | Caso crítico a cubrir | Tipo de test | Por qué duele si falla |
|---|---|---|---|
| **D1 — metadata** | Serializar `TagValueMetadata.Carrera(fecha, distancia)` → JSONB → deserializar al mismo objeto. JSON corrompido (campo faltante, tipo equivocado) → mapeador devuelve `Vacia` **y** loguea con `traceId`. | Unitario del mapeador | Sin este test, un JSON corrupto silencioso aterriza en el dominio y rompe lecturas en cascada. |
| **D2 — unicidad** | Crear dos `TagKey` con `"Nivel"` y `"nivel "` (espacios + mayúsculas) → el agregado `Taxonomía` rechaza el segundo con error de dominio `EtiquetaDuplicada`, **antes** de tocar la BD. La misma escritura concurrente burlando el agregado → el índice `UNIQUE (club_id, unaccent(lower(trim(nombre))))` la rechaza en BD. | Unitario del agregado + integración con Testcontainers (índice real) | Sin la doble defensa, datos sucios (`nivel`/`Nivel`/`Nível`) en cuanto haya concurrencia. |
| **D3 — pertenencia AND** | El SQL canónico devuelve correctamente: alumno con todos los tags → está; alumno con solo algunos → no está; alumno con tags repetidos en `alumno_tag` (datos sucios) → cuenta una sola vez gracias a `COUNT(DISTINCT)`; grupo sin tags requeridos → conjunto vacío salvo incluidos manuales. | Integración con Testcontainers | El `COUNT(*)` ingenuo es el bug más común en este patrón. |
| **D4 — overrides** | Alumno excluido manualmente aunque cumpla tags → no está. Alumno incluido manualmente sin cumplir tags → está. Alumno con ambos overrides (`incluido=true` Y `incluido=false`) → no está (`EXCEPT` gana). | Integración con Testcontainers | Garantiza la regla de prevalencia del excluido. |
| **D5 — snapshot** | Publicar el plan congela la membresía: cambios posteriores en tags **no** alteran el snapshot. Sacar al alumno del grupo después de publicar → su sesión sigue visible hasta el final de la semana. | Integración del módulo Planificación | Si se rompe, el alumno deja de ver su plan inesperadamente, peor experiencia posible. |
| **D6 — ritmo** | Construir `Ritmo.Absoluto(210)` → formato `"3:30/km"`. Construir `Ritmo.Relativo(distancia=10K, deltaSegPorKm=+10)` → al resolver con marca 47:30 (= 4:45/km) sale 4:55/km. Delta negativo → ritmo más rápido. Delta cero → ritmo igual a la marca. | Unitario del value object + función de resolución | Estos cálculos los ve el alumno cada día; un signo invertido es bug que se nota inmediatamente. |
| **D7 — privacidad de marcas** | Un endpoint o caso de uso de entrenador/admin que intente leer `seguimiento.marca_alumno` falla en compilación (regla ArchUnit) o en autorización (ADR-0009). No hay forma legítima de que el frontend del entrenador pinte un valor de marca. | Test de arquitectura ArchUnit + integración del módulo | La privacidad es invariante de diseño; sin guardarraíl técnico, alguien la rompe con un PR bienintencionado. |
| **D8 — read model resuelto** | Tras `MarcaActualizada`, el read model `plan_resuelto_por_alumno` recalcula filas donde `ritmo_referencia_distancia = distancia` del alumno. Si el alumno no tiene la marca, `ritmo_calculado_seg_por_km = NULL` y `ritmo_falta_marca = referencia`. | Integración del módulo Seguimiento (consumidores idempotentes) | Si el read model no se recalcula, el alumno ve un ritmo viejo después de actualizar su marca → pierde confianza en la app. |
| **D9 — personalización** | `editar(plan, sesion, alumno)` dos veces → una sola fila en `personalizacion` (constraint `UNIQUE` + invariante del agregado). `resolverSesionParaAlumno` devuelve override si existe, base si no. Mensaje al alumno propaga al read model D8. | Unitario del agregado `PlanSemanal` + integración | Si se duplica, el alumno ve dos sesiones distintas el mismo día. |
| **Reglas de oro** | Test ArchUnit: ninguna clase de `infrastructure` declara una enum/constante de "distancia"; sólo se permite el `Distancia` del dominio común. Ninguna clase manipula `metadata JSONB` como mapa genérico fuera del mapeador autorizado. | ArchUnit | Sin esto, las reglas de oro son sólo papel. |

Los tests de **D3, D4, D5** corren sobre datos sintéticos a un orden de magnitud cercano a los NFRs holgados (no a los 5.000 alumnos del extremo, pero sí a unos cientos): el objetivo es verificar **correctitud** del SQL canónico, no rendimiento puro. La medición de rendimiento contra los volúmenes objetivo es una tarea aparte y vive en el plan de pruebas de carga del Hito H3 (`plan-implementacion-mvp.md`).

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
- Añadir el feature "ritmos relativos por marcas" (H5) no requirió migración del esquema diseñado en el día 1: solo cambió el modelo de `Ritmo` para reflejar mejor cómo lo piensan los entrenadores.
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

- **Path de migración a `OR` / `NOT` en queries de grupo** (evolución prevista, no MVP). Cuando aparezca el primer caso de uso real *"nivel medio **o** medio-alto"* o *"todos los del grupo X que **no** estén en estado lesión"*, el camino preferido es pasar de la tabla plana `grupo_tag_requerido` (que codifica AND implícito) a un **árbol de expresión normalizado**:
  ```
  grupo_expresion (id, grupo_id, operador ∈ {AND, OR, NOT}, padre_id)
  grupo_expresion_hoja (expresion_id, tag_value_id)
  ```
  Es migración **acotada**: los grupos existentes se transforman a un árbol trivial con un único nodo `AND` raíz que cuelga de cada `tag_value_id` actual. El SQL canónico de D3 se sustituye por una evaluación recursiva del árbol (CTE recursiva o evaluación en aplicación según el rendimiento medido). Alternativas que **no** se elegirán salvo motivo de peso: (a) JSON con AST en `Grupo.expression JSONB` — más simple pero menos indexable; (b) un DSL textual parseado en runtime — fragilidad innecesaria. El detonante para abrir el ADR de migración es la **segunda** petición real, no la primera (la primera se resuelve con un override manual en muchos casos).
- **Path de histórico de marcas del corredor** (evolución prevista, COULD del backlog). Hoy `seguimiento.marca_alumno` guarda solo la **marca vigente** con PK `(alumno_id, distancia)`. Cuando se decida soportar histórico (para gráficas de progresión, validaciones cruzadas o auditoría), el camino preferido es **aditivo**: crear una tabla nueva `seguimiento.marca_alumno_historico (alumno_id, distancia, tiempo_segundos, fecha_marca, registrado_en)` que recibe una fila en cada `MarcaActualizada`, **manteniendo `marca_alumno` intacta** como cache de "lo vigente". Ventaja: cero migración destructiva, los read models y el código de resolución de ritmos siguen leyendo `marca_alumno` sin cambios. Alternativas que **no** se elegirán salvo motivo de peso: (a) convertir `marca_alumno` en tabla con histórico (PK `(alumno_id, distancia, fecha)`) — rompe la lectura de "marca vigente" y obliga a `DISTINCT ON` o subconsultas; (b) source-of-truth en eventos `marca_alumno_evento` con `marca_alumno` como projection — sobreingeniería para el caso de uso esperado. El detonante para abrir el ADR es la entrada de la feature *"ver progresión de mis marcas en gráfico"* (hoy ★ en SHOULD de la sección post-MVP del backlog) o una exigencia regulatoria de auditoría que no es nuestro caso hoy.
- La generalización multi-club ya está soportada por el esquema: `club_id` está en todas las tablas desde el día 1 (ADR-0006).
- Revisar el modelo de snapshot si los entrenadores piden que el plan publicado "siga vivo" ante cambios de tags — hoy se asume congelado.
- Si las carreras ganan features propias (resultados, inscripciones, dorsales), se evaluará promover el `TagValue` de carrera a una entidad `Carrera` tipada — ADR futuro, hoy innecesario.
- Si en el futuro las `TagKey` / `TagValue` se pueden archivar, la unicidad pasará a un índice único **parcial** (solo sobre las activas). Hoy no se contempla el archivado de tags.
- **Revisión del 2026-05-27 (Nivel 1 + Personalización)**: el ADR se reestructura con índice, premisas heredadas y numeración D1-D9, alineándose con el patrón usado en ADR-0001. Se incorpora **D9 — Personalización como entidad hija de `PlanSemanal`** (M12), que vivía hasta ahora solo en `plan-implementacion-mvp.md`; el ADR del modelo de datos debe reflejarla porque es decisión nuclear del módulo Planificación, no detalle de implementación.
