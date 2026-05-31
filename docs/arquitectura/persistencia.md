# Persistencia por módulo — guía de referencia

Subdocumento de [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md). Cubre los **detalles de persistencia** que la guía principal resume: esquema por módulo, sin FK cruzado entre esquemas, outbox compartido, Konvert detallado, migraciones Flyway, JSONB para value objects, snapshot de proyecciones, tabla `evento_procesado` para idempotencia.

> Espejo aplicado de **ADR-0004** (PostgreSQL, esquema por módulo), **ADR-0007 D6/D15** (outbox + compactación), **ADR-0008 D6** (dominio puro con persistencia separada). Si hay conflicto, gana el ADR.

## 1. Esquema por módulo

Cada módulo tiene su **propio esquema** en PostgreSQL (ADR-0004 D7). El nombre del esquema coincide con el nombre del módulo en castellano.

| Módulo | Esquema | Tablas típicas |
|---|---|---|
| Identidad y acceso | `identidad` | `usuario`, `consentimiento`, `evento_auditoria`, `evento_procesado` |
| Club y taxonomía | `club` | `club`, `tag_key`, `tag_value`, `alumno_tag`, `grupo`, `evento_procesado` |
| Planificación | `planificacion` | `plan_semanal`, `sesion`, `personalizacion`, `miembros_grupo` (proyección), `snapshot_miembros_grupo`, `evento_procesado` |
| Seguimiento (salud) | `salud` | `alumno_perfil`, `marca`, `reporte_sesion`, `alerta`, `evento_procesado` |
| Auditoría | `auditoria` | `evento` |

### Reglas de aislamiento entre esquemas (ADR-0004 D7)

1. **Ninguna `FOREIGN KEY` cruza esquemas**. Las únicas FK son intra-esquema (`planificacion.sesion.plan_id → planificacion.plan_semanal.id`).
2. **Ninguna consulta cruza esquemas**. Si el módulo Planificación necesita saber los alumnos de un grupo, lee de su **proyección local** `planificacion.miembros_grupo`, no de `club.alumno_tag`.
3. **Referencias entre módulos son UUID sin FK**. Por ejemplo, `planificacion.plan_semanal.entrenador_id` es `UUID NOT NULL` pero **no tiene** FK a `identidad.usuario.id`. El módulo confía en que el evento `EntrenadorCreado` consumido por su proyección garantiza la coherencia eventual.
4. **Las migraciones de un módulo no tocan tablas de otro**. Si una migración necesita datos de otro módulo, se hace via evento (republicado si hace falta), no via JOIN cruzado en la migración.

### Esquema `public` (mínimo) y `_shared/` para infraestructura compartida

- **`public.event_publication`** — outbox de Spring Modulith (lo gestiona el framework, no hay que crearlo a mano).
- **`public.flyway_schema_history`** — historial Flyway (compartido para todo el backend).

## 2. Convención de nombres

### Idioma

Castellano coherente con el **lenguaje ubicuo** (ADR-0008, glosario). El equipo lee el mismo nombre en código y en SQL.

```sql
-- ✅ Correcto: castellano alineado con el código y el glosario
CREATE TABLE planificacion.plan_semanal (...);
CREATE TABLE salud.reporte_sesion (...);
CREATE TABLE planificacion.miembros_grupo (...);

-- ❌ Incorrecto: rompe el lenguaje ubicuo
CREATE TABLE planificacion.weekly_plan (...);
CREATE TABLE planificacion.weeklyPlan (...);    -- camelCase no se usa en SQL
```

**Excepciones aceptadas** (uso técnico estable):

- `id`, `created_at`, `updated_at`, `version` (convención JPA/SQL).
- `last_processed_event_id`, `last_processed_event_ts` (técnico, parte del patrón).

### Estilo

- `snake_case` siempre. Sin camelCase, sin PascalCase.
- Sin caracteres especiales en identificadores: `entrenador` (no `entrenador_ñ`), `tamano` (no `tamaño`).
- Singular para nombres de tabla: `usuario` (no `usuarios`).
- Plural sólo cuando es un agregado natural en plural: `miembros_grupo` (la proyección agrupa varios alumnos por grupo).

## 3. Tipos canónicos

| Concepto | Tipo SQL | Notas |
|---|---|---|
| Identificadores | `UUID NOT NULL` | UUID v7 generado **en aplicación** (no en BD). Sin extensión `uuid-ossp` ni `gen_random_uuid()` del lado servidor. |
| Marca temporal | `TIMESTAMPTZ NOT NULL` | Siempre con zona horaria. Almacenado en UTC; presentado en zona del club. ADR-0015 A3. |
| Texto corto identificable | `VARCHAR(N)` | Con `N` razonable: `VARCHAR(120)` para nombres, `VARCHAR(254)` para emails. |
| Texto libre | `TEXT` | Sin límite SQL. Validación del tamaño en dominio. |
| Booleano | `BOOLEAN NOT NULL DEFAULT FALSE` | Sin tres-valued logic. |
| Value object complejo | `JSONB NOT NULL` | Con `CHECK` constraints contra estructura (sección 4). |
| Enum estable | `VARCHAR(40)` con `CHECK (col IN (...))` | No usar `ENUM` nativo de Postgres (migrar es caro). |
| Numeric monetario o decimal | `NUMERIC(p, s)` | Nunca `FLOAT` para dinero o ritmos. |
| Auto-incremento opcional | `BIGINT GENERATED ALWAYS AS IDENTITY` | Sólo para tablas internas (auditoría, log). Las entidades del dominio usan UUID v7. |

### Política UUID

- **Generación en aplicación**: `UuidCreator.getTimeOrderedEpoch()` o equivalente.
- **Por qué UUID v7**: ordenable temporalmente (mejora locality del índice), igual de aleatorio que v4 para evitar adivinar IDs (cruce ADR-0003 D2).
- **No se valida en SQL**: `CHECK` por versión del UUID es overkill. Si llega un v4 por error, el código y los tests lo detectan.

## 4. JSONB para value objects con shape variable

Los **value objects** con varias formas (`sealed class`) o con shape flexible se persisten como `JSONB`. Las **entidades con identidad propia** se persisten normalizadas.

### Cuándo usar JSONB

- `Ritmo` (sealed: `Absoluto | Relativo`): JSONB.
- `Personalizacion.override` (misma forma que `Sesion`, opcional): JSONB.
- `auditoria.evento.metadata` (`Map<String, Any>` específico al tipo de evento): JSONB.
- `Consentimiento.payload` (versión del texto + IP + user-agent): JSONB.

### Cuándo NO usar JSONB

- `Sesion` (entidad hija del agregado `PlanSemanal` con identidad y ciclo de vida): tabla normalizada `planificacion.sesion` con FK a `plan_semanal`.
- `Alumno`, `Usuario`, `Reporte` (entidades raíz): tablas propias.
- Cualquier dato que se consulte frecuentemente con filtros, agregaciones o joins.

### `CHECK` contra estructura

JSONB sin estructura es **basura estructurada**. Cada columna `JSONB` lleva un `CHECK` mínimo:

```sql
CREATE TABLE planificacion.sesion (
    id         UUID PRIMARY KEY,
    plan_id    UUID NOT NULL REFERENCES planificacion.plan_semanal(id) ON DELETE CASCADE,
    dia        SMALLINT NOT NULL CHECK (dia BETWEEN 0 AND 6),  -- 0 = lunes
    tipo       VARCHAR(40) NOT NULL CHECK (tipo IN (
                   'RODAJE', 'SERIES', 'TEMPO', 'TIRADA_LARGA', 'FARTLEK',
                   'CUESTAS', 'PROGRESIVO', 'FUERZA', 'COMPETICION', 'DESCANSO'
               )),
    distancia_metros  INT NULL CHECK (distancia_metros > 0),
    ritmo      JSONB NOT NULL CHECK (
        (ritmo->>'tipo' IN ('Absoluto', 'Relativo'))
        AND (ritmo->>'tipo' <> 'Absoluto' OR (ritmo->>'segPorKm' IS NOT NULL))
        AND (ritmo->>'tipo' <> 'Relativo' OR (ritmo->>'referencia' IS NOT NULL AND ritmo->>'deltaSegPorKm' IS NOT NULL))
    ),
    descripcion       TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

El `CHECK` no sustituye la validación del dominio (que es más completa); es **defensa en profundidad**. Si alguien escribe directamente en la BD saltándose el dominio (raro, sólo migraciones), el `CHECK` detecta los casos imposibles.

### Indexación de JSONB

Para campos JSONB que se consulten:

```sql
-- Búsqueda por tipo de ritmo (índice expresión)
CREATE INDEX idx_sesion_ritmo_tipo ON planificacion.sesion ((ritmo->>'tipo'));

-- Búsqueda dentro del payload completo (GIN índice general)
CREATE INDEX idx_auditoria_metadata ON auditoria.evento USING GIN (metadata);
```

## 5. `CHECK` constraints como defensa en profundidad

Sólo los invariantes **universales** que nunca dependen de reglas de negocio cambiantes:

```sql
CREATE TABLE planificacion.plan_semanal (
    id              UUID PRIMARY KEY,
    club_id         UUID NOT NULL,                          -- multi-tenant (ADR-0006 D22)
    entrenador_id   UUID NOT NULL,                          -- sin FK (cruza esquemas)
    grupo_id        UUID NOT NULL,                          -- sin FK (cruza esquemas)
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('BORRADOR', 'PUBLICADO', 'ARCHIVADO')),
    semana_inicio   DATE NOT NULL,
    semana_fin      DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,              -- @Version JPA
    CHECK (semana_fin > semana_inicio),
    UNIQUE (club_id, entrenador_id, grupo_id, semana_inicio)
);

CREATE INDEX idx_plan_semanal_club_id ON planificacion.plan_semanal(club_id);
CREATE INDEX idx_plan_semanal_grupo_id ON planificacion.plan_semanal(grupo_id);
```

**Lo que SÍ va en SQL**:

- `NOT NULL` para campos obligatorios.
- `CHECK` para enums estables (`estado IN ('BORRADOR', ...)`).
- `CHECK` para invariantes universales (`semana_fin > semana_inicio`).
- `UNIQUE` para evitar duplicados estructurales.
- `FOREIGN KEY` **sólo intra-esquema** (cruzar esquemas viola ADR-0004 D7).
- Índices por `club_id` y por las columnas de filtrado frecuente (cruce con `@AuthScope`).

**Lo que NO va en SQL** (vive en el dominio):

- Reglas de negocio complejas tipo *"un plan publicado no puede modificarse"* — vive en `PlanSemanal.publicar()`.
- Cálculos derivados.
- Validaciones que dependen del rol del usuario o de relaciones de otros módulos.

## 6. Outbox de Spring Modulith

El outbox vive en **`public.event_publication`**, gestionado por el framework. **No se crea ni modifica a mano** — Spring Modulith lo crea con `spring.modulith.events.jdbc.schema-initialization.enabled=true` o vía la migración Flyway que el equipo de Modulith publica.

### Estructura (gestionada por Modulith)

```sql
-- Esta tabla la crea Spring Modulith automáticamente; aquí solo para referencia
CREATE TABLE public.event_publication (
    id                 UUID PRIMARY KEY,
    listener_id        VARCHAR(512) NOT NULL,
    event_type         VARCHAR(512) NOT NULL,
    serialized_event   TEXT NOT NULL,
    publication_date   TIMESTAMP NOT NULL,
    completion_date    TIMESTAMP
);
```

### Política de fallos (cruce ADR-0007 D13)

- 5 reintentos con backoff exponencial 1/2/4/8/16 s.
- Tras agotar reintentos, `completion_date IS NULL` → DLQ implícita.
- Alarma `outbox_dlq_events > 0` (ADR-0011 D10).
- Republicación admin via `POST /admin/events/republish`.

### Compactación a 30 días (cruce ADR-0007 D15)

Un job programado borra eventos con `completion_date < now() - INTERVAL '30 days'`. Eventos sin completar **no se compactan** — siguen en DLQ hasta resolverse.

## 7. Idempotencia de listeners: tabla `evento_procesado`

Cada módulo tiene su tabla `evento_procesado` para garantizar que un listener no procesa dos veces el mismo evento:

```sql
CREATE TABLE planificacion.evento_procesado (
    listener      VARCHAR(120) NOT NULL,
    event_id      UUID NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (listener, event_id)
);

-- Limpieza periódica (cron diario)
DELETE FROM planificacion.evento_procesado
WHERE processed_at < now() - INTERVAL '30 days';
```

Uso en el listener:

```kotlin
@Component
class AlumnoAsignadoAGrupoListener(
    private val proyeccion: MiembrosGrupoProjection,
    private val tracker: EventoProcesadoTracker,
) {
    @ApplicationModuleListener
    fun on(evento: AlumnoAsignadoAGrupo) {
        if (!tracker.marcarSiNuevo(listener = "AlumnoAsignadoAGrupoListener", eventId = evento.eventId)) {
            return  // ya procesado
        }
        proyeccion.añadir(evento.grupoId, evento.alumnoId, evento.occurredAt)
    }
}

@Component
class EventoProcesadoTracker(private val jdbc: JdbcTemplate) {
    /**
     * INSERT IF NOT EXISTS en {modulo}.evento_procesado.
     * Devuelve true si fue insertado (procesar), false si ya existía (saltar).
     */
    fun marcarSiNuevo(listener: String, eventId: UUID): Boolean {
        val sql = """
            INSERT INTO planificacion.evento_procesado(listener, event_id)
            VALUES (?, ?)
            ON CONFLICT (listener, event_id) DO NOTHING
        """.trimIndent()
        return jdbc.update(sql, listener, eventId) == 1
    }
}
```

Política de retención: 30 días alineado con la compactación del outbox.

## 8. Proyecciones locales

Las **proyecciones locales** materializan datos de otros módulos sin acoplamiento síncrono (ADR-0007 D8, ADR-0009 D8). Tienen dos columnas obligatorias:

- `last_processed_event_id UUID NOT NULL` — último evento que actualizó esta fila.
- `last_processed_event_ts TIMESTAMPTZ NOT NULL` — momento del último evento procesado.

### Ejemplo: proyección de miembros de grupo en Planificación

```sql
CREATE TABLE planificacion.miembros_grupo (
    grupo_id                  UUID PRIMARY KEY,
    club_id                   UUID NOT NULL,
    alumnos                   UUID[] NOT NULL DEFAULT ARRAY[]::UUID[],
    last_processed_event_id   UUID NOT NULL,
    last_processed_event_ts   TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_miembros_grupo_club_id ON planificacion.miembros_grupo(club_id);
CREATE INDEX idx_miembros_grupo_alumnos ON planificacion.miembros_grupo USING GIN (alumnos);
```

### Cálculo del lag para política stale

```kotlin
@Component
class MiembrosGrupoProjection(private val jdbc: JdbcTemplate) {

    /**
     * Lag = now() - máximo last_processed_event_ts de la proyección.
     * Si > 60s, fail-closed en AutorizacionService (ADR-0009 D9).
     */
    fun lagSegundos(): Long {
        val sql = """
            SELECT EXTRACT(EPOCH FROM (now() - COALESCE(MAX(last_processed_event_ts), now())))::BIGINT
            FROM planificacion.miembros_grupo
        """.trimIndent()
        return jdbc.queryForObject(sql, Long::class.java) ?: 0L
    }

    fun añadir(grupoId: GrupoId, alumnoId: AlumnoId, eventId: UUID, occurredAt: Instant) {
        val sql = """
            INSERT INTO planificacion.miembros_grupo
                (grupo_id, club_id, alumnos, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ARRAY[?]::UUID[], ?, ?)
            ON CONFLICT (grupo_id) DO UPDATE SET
                alumnos = (
                    SELECT ARRAY(SELECT DISTINCT unnest(planificacion.miembros_grupo.alumnos || EXCLUDED.alumnos))
                ),
                last_processed_event_id = EXCLUDED.last_processed_event_id,
                last_processed_event_ts = EXCLUDED.last_processed_event_ts,
                updated_at = now()
        """.trimIndent()
        jdbc.update(sql, grupoId.value, currentClubId(), alumnoId.value, eventId, Timestamp.from(occurredAt))
    }
}
```

## 9. Snapshot semanal de proyecciones

El outbox se compacta a 30 días (sección 6). Para reproyectar histórico, cada proyección guarda **un snapshot semanal** en una tabla espejo:

```sql
CREATE TABLE planificacion.snapshot_miembros_grupo (
    snapshot_at   TIMESTAMPTZ NOT NULL,
    contenido     JSONB NOT NULL,
    PRIMARY KEY (snapshot_at)
);

CREATE INDEX idx_snapshot_miembros_grupo_at ON planificacion.snapshot_miembros_grupo(snapshot_at DESC);
```

Un job programado semanal (Spring `@Scheduled` o equivalente) serializa el contenido completo de la proyección a JSONB y lo persiste. Se mantienen **8 semanas** de snapshots; los más antiguos se purgan.

### Reproyección desde snapshot

Endpoint admin `POST /admin/proyecciones/planificacion/miembros_grupo/reproyectar`:

1. Lee el snapshot más reciente.
2. Trunca `planificacion.miembros_grupo` y restaura desde el snapshot.
3. Lee del outbox los eventos con `publication_date >= snapshot_at` y los reaplica al listener.
4. Reporta progreso por métrica `reproyeccion_eventos_procesados_total`.

## 10. Migraciones Flyway

### Estructura de carpetas

```
backend/src/main/resources/db/migration/
├── _shared/                          ← infraestructura compartida
│   └── V202605270001__configura_event_publication.sql
├── identidad/
│   ├── V202605270100__crea_usuario.sql
│   ├── V202605280100__anade_consentimiento.sql
│   └── V202605290100__crea_evento_auditoria.sql
├── club/
│   ├── V202605270200__crea_club.sql
│   ├── V202605270201__crea_tag_key_y_tag_value.sql
│   └── V202605280200__crea_grupo.sql
├── planificacion/
│   ├── V202605270300__crea_plan_semanal.sql
│   ├── V202605270301__crea_sesion.sql
│   ├── V202605280300__crea_personalizacion.sql
│   └── V202605290300__crea_miembros_grupo_proyeccion.sql
├── salud/
│   └── ...
└── auditoria/
    └── ...
```

### Convención del nombre de migración

`V{YYYYMMDDHHMM}__{descripcion_corta}.sql`:

- **Timestamp**: año-mes-día-hora-minuto. Garantiza orden global sin conflictos entre PRs paralelas.
- **Descripción corta**: snake_case, verbos en infinitivo o presente: `crea_usuario`, `anade_columna_estado`, `migra_ritmo_a_jsonb`.

Configuración Flyway:

```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration/_shared
      - classpath:db/migration/identidad
      - classpath:db/migration/club
      - classpath:db/migration/planificacion
      - classpath:db/migration/salud
      - classpath:db/migration/auditoria
    schemas: identidad, club, planificacion, salud, auditoria, public, auditoria
    default-schema: public
    validate-on-migrate: true
```

### Migraciones compatibles hacia atrás (ADR-0010 D11)

Cualquier migración respeta la regla de **deploy-then-migrate**: la nueva versión de la app debe poder correr con el esquema viejo Y con el nuevo. Migraciones breaking se hacen en dos pasos:

1. Añadir columna nullable o tabla nueva (la app vieja la ignora).
2. Migrar datos en una tarea separada.
3. Cuando la app nueva está en producción y validada, segunda migración hace `NOT NULL` o borra lo viejo.

## 11. Konvert para mapping (ejemplos detallados)

[Konvert](https://github.com/mcarleio/konvert) genera el mapper en tiempo de compilación (sin reflection) — coherente con ADR-0008 D6 (dominio puro).

### Mapper dominio ↔ entidad JPA

```kotlin
// infrastructure/persistencia/PlanSemanalEntity.kt
@Entity
@Table(name = "plan_semanal", schema = "planificacion")
class PlanSemanalEntity(
    @Id
    @Column(name = "id")
    var id: UUID,

    @Column(name = "club_id", nullable = false)
    var clubId: UUID,

    @Column(name = "entrenador_id", nullable = false)
    var entrenadorId: UUID,

    @Column(name = "estado", nullable = false, length = 20)
    var estado: String,

    @Column(name = "semana_inicio", nullable = false)
    var semanaInicio: LocalDate,

    @Column(name = "semana_fin", nullable = false)
    var semanaFin: LocalDate,

    @OneToMany(mappedBy = "planId", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sesiones: MutableList<SesionEntity>,

    @Version
    var version: Long = 0,

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)

// infrastructure/persistencia/PlanSemanalMapper.kt
@Konverter
interface PlanSemanalMapper {

    @Konvert(mappings = [
        Mapping(target = "id",            source = "id",            converter = PlanIdConverter::class),
        Mapping(target = "clubId",        source = "clubId",        converter = ClubIdConverter::class),
        Mapping(target = "entrenadorId",  source = "entrenadorId",  converter = EntrenadorIdConverter::class),
        Mapping(target = "estado",        source = "estado",        converter = EstadoPlanConverter::class),
    ])
    fun aDominio(entity: PlanSemanalEntity): PlanSemanal

    @Konvert(mappings = [
        Mapping(target = "id",            source = "id.value"),
        Mapping(target = "clubId",        source = "clubId.value"),
        Mapping(target = "entrenadorId",  source = "entrenadorId.value"),
        Mapping(target = "estado",        source = "estado.name"),
    ])
    fun aEntidad(dominio: PlanSemanal): PlanSemanalEntity
}
```

### Custom converters para Typed IDs y enums

```kotlin
// infrastructure/persistencia/converters.kt
class PlanIdConverter : TypeConverter<UUID, PlanId> {
    override fun convert(value: UUID): PlanId = PlanId(value)
}

class EstadoPlanConverter : TypeConverter<String, EstadoPlan> {
    override fun convert(value: String): EstadoPlan = EstadoPlan.valueOf(value)
}
```

### Mapper JSONB ↔ value object

Para `Ritmo` (sealed class) en JSONB, Konvert no cubre serialización JSON; se delega a Jackson con un `@Converter` JPA:

```kotlin
// infrastructure/persistencia/RitmoJsonbConverter.kt
@Converter(autoApply = false)
class RitmoJsonbConverter(private val objectMapper: ObjectMapper) : AttributeConverter<Ritmo, String> {
    override fun convertToDatabaseColumn(attribute: Ritmo?): String? =
        attribute?.let { objectMapper.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): Ritmo? =
        dbData?.let { objectMapper.readValue(it, Ritmo::class.java) }
}

// En la entidad:
@Column(name = "ritmo", columnDefinition = "jsonb", nullable = false)
@Convert(converter = RitmoJsonbConverter::class)
var ritmo: Ritmo
```

Jackson serializa `sealed class Ritmo` con `@JsonTypeInfo(use = NAME, property = "tipo")` y `@JsonSubTypes`.

### Mapper dominio ↔ DTO REST

DTOs propios separados del dominio:

```kotlin
// infrastructure/rest/dto/PlanResponse.kt
data class PlanResponse(
    val id: UUID,
    val estado: String,
    val semanaInicio: LocalDate,
    val semanaFin: LocalDate,
    val sesiones: List<SesionResponse>,
)

// infrastructure/rest/PlanRestMapper.kt
@Konverter
interface PlanRestMapper {

    @Konvert(mappings = [
        Mapping(target = "id",     source = "id.value"),
        Mapping(target = "estado", source = "estado.name"),
    ])
    fun aResponse(plan: PlanSemanal): PlanResponse
}
```

### Reglas para los mappers

- **No hay mappers globales** (un solo `Mapper` que mapea todo). Cada par tipo-tipo tiene su `@Konverter`.
- **No mezclar mappers JPA con mappers REST**. `PlanSemanalMapper` (JPA) y `PlanRestMapper` (REST) son interfaces separadas.
- **Custom converters reutilizables** en `infrastructure/persistencia/converters.kt` (Typed IDs, enums comunes).

## 12. Retención por categoría (cruce ADR-0014 D10)

Cada tabla del módulo declara su categoría de dato (ADR-0014 D5). La retención es competencia del SQL + cron:

| Categoría | Ejemplo de tabla | Retención | Mecanismo |
|---|---|---|---|
| **1 — PII primaria** | `identidad.usuario`, `salud.alumno_perfil`, `salud.reporte_sesion`, `salud.marca` | Hasta baja + 30 días de gracia | Borrado físico al consumir `AlumnoEliminado` (ver [`rgpd-en-modulos.md`](rgpd-en-modulos.md)) |
| **2 — Auditoría identidad** | `identidad.evento_auditoria` | 12 meses | Cron mensual `DELETE WHERE ts < now() - INTERVAL '12 months'` |
| **3 — Auditoría autorización** | `auditoria.evento` | 24 meses | Cron mensual `DELETE WHERE ts < now() - INTERVAL '24 months'` |
| **4 — Outbox** | `public.event_publication` | 30 días | Compactación de Spring Modulith |
| **4 — Idempotencia** | `{modulo}.evento_procesado` | 30 días | Cron diario |
| **5 — Backups** | RDS snapshots | 30 días | Retención automática RDS |
| **6 — Logs operativos** | CloudWatch Logs (no SQL) | 90 días | Retención de CloudWatch |
| **Proyecciones** | `planificacion.miembros_grupo`, etc. | Mientras el módulo origen viva | Reproyección desde snapshot si hace falta |
| **Snapshots** | `planificacion.snapshot_miembros_grupo` | 8 semanas | Cron semanal mantiene 8, purga el resto |

Cada migración que crea una tabla nueva debe **declarar en comentario** su categoría:

```sql
-- Categoría 1: PII primaria. Retención: hasta baja + 30 días de gracia. Borrado físico al consumir AlumnoEliminado.
CREATE TABLE salud.alumno_perfil (
    ...
);
```

## 13. Checklist de persistencia al crear un módulo

- [ ] Esquema propio `{modulo}` creado en migración inicial `(ADR-0004 D7)`
- [ ] Ninguna `FOREIGN KEY` ni consulta cruza esquemas `(ADR-0004 D7)`
- [ ] Cada tabla tiene comentario con su **categoría RGPD** (1-6) `(ADR-0014 D5)`
- [ ] Nombres en castellano coherentes con el glosario `(ADR-0008)`
- [ ] `UUID v7` generado en aplicación; `TIMESTAMPTZ` siempre con zona `(ADR-0008)`
- [ ] `CHECK` constraints solo para invariantes universales (enums, NOT NULL, rangos)
- [ ] Value objects con shape variable en `JSONB` con `CHECK` contra estructura
- [ ] Entidades con identidad propia en tablas normalizadas
- [ ] Índice por `club_id` en cada tabla con datos del dominio `(ADR-0006 D22, ADR-0009 D4)`
- [ ] Proyecciones locales tienen columnas `last_processed_event_id` y `last_processed_event_ts` `(ADR-0009 D9)`
- [ ] Tabla `{modulo}.evento_procesado(listener, event_id)` UNIQUE creada `(ADR-0007 D9)`
- [ ] Snapshot semanal de cada proyección + endpoint admin de reproyección `(ADR-0007 D15)`
- [ ] Mappers Konvert separados por par tipo-tipo (no mappers globales) `(ADR-0008 D6)`
- [ ] Migraciones Flyway en `db/migration/{modulo}/` con `V{YYYYMMDDHHMM}__descripcion.sql` `(ADR-0010 D11)`
- [ ] Migraciones compatibles hacia atrás (deploy-then-migrate) `(ADR-0010 D11)`
- [ ] Política de retención de cada tabla cruzada a [ADR-0014 D10](../adr/0014-proteccion-de-datos-rgpd.md#d10)

## Referencias

- **ADR-0004** — base de datos PostgreSQL y esquema por módulo.
- **ADR-0006 D22** — `club_id` en todas las tablas.
- **ADR-0007 D6, D9, D13, D15** — outbox, idempotencia, política de fallos, compactación.
- **ADR-0008 D6, D11** — dominio puro con persistencia separada; `Either` en lugar de excepciones.
- **ADR-0009 D4, D9, D11** — `club_id` en repositorios, política stale, `@AuthScope`.
- **ADR-0010 D11** — migraciones Flyway compatibles hacia atrás.
- **ADR-0014 D5, D6, D7, D9, D10** — categorización de datos, borrado mixto, retención.
- **ADR-0011 D9** — anonimización de IP en logs operativos (cruce con tablas de auditoría).
- [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md) — guía principal.
- [`rgpd-en-modulos.md`](rgpd-en-modulos.md) — detalles del consumo de `AlumnoEliminado` y borrado mixto.
- [`testing-de-modulos.md`](testing-de-modulos.md) — Testcontainers PostgreSQL para tests de integración.
