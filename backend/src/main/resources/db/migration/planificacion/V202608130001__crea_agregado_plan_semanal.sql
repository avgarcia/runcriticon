-- Agregado WeeklyPlan (LAL-114, arranque del módulo): plan_semanal es la raíz, sesion y personalizacion sus
-- entidades hijas (ADR-0008 D17, carga eager). Las tres tablas se crean juntas aunque personalizacion no
-- tenga caso de uso todavía (ADR-0002 D9: "ciudadano de primera del agregado, no se añade después") — evita
-- una migración de deuda cuando LAL-26 le dé caso de uso real.
--
-- Nombres de tabla en castellano (identificadores SQL, ADR-0008 D4); columnas de negocio en castellano,
-- igual que el resto del esquema `planificacion`.

-- Categoría RGPD: PII_PRIMARIA (categoría 1). `entrenador_id` vincula el plan a la persona que lo creó —
-- misma categoría y mismo motivo que `club_taxonomia.grupo_entrenador` (referencia a persona, sin nombre ni
-- email propios). Sin FK a `identidad` ni a `club_taxonomia` (ADR-0004: ningún esquema de módulo cruza a
-- otro); `grupo_id` se resuelve contra la proyección local `miembro_grupo`, no contra una tabla ajena.
CREATE TABLE planificacion.plan_semanal (
    id             UUID                     NOT NULL,
    club_id        UUID                     NOT NULL,
    grupo_id       UUID                     NOT NULL,
    entrenador_id  UUID                     NOT NULL,
    semana         DATE                     NOT NULL,
    estado         VARCHAR(20)              NOT NULL,
    creado_en      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT plan_semanal_pk PRIMARY KEY (id),
    -- Defensa en profundidad: los valores válidos los impone el dominio (PlanStatus); el CHECK evita que una
    -- fila escrita fuera de la aplicación quede con un valor que el mapeador no sepa leer.
    CONSTRAINT plan_semanal_estado_check CHECK (estado IN ('BORRADOR', 'PUBLICADO'))
);

-- Soporta la pantalla "mis planes en borrador por grupo" (AC7) y la comprobación de unicidad operativa (un
-- entrenador no debería duplicar plan para el mismo grupo/semana, aunque ese invariante no se exige todavía).
CREATE INDEX plan_semanal_club_grupo_idx ON planificacion.plan_semanal (club_id, grupo_id);
CREATE INDEX plan_semanal_club_entrenador_idx ON planificacion.plan_semanal (club_id, entrenador_id);

-- Categoría RGPD: SIN_PII. Contenido de entrenamiento del plan (día, ritmo objetivo); no referencia a ninguna
-- persona por sí misma — la personalización por alumno vive en `personalizacion`, no aquí.
--
-- Columnas de ritmo (ADR-0002 D6): `ritmo_tipo` decide cuáles de las otras tres aplican (ABSOLUTO usa solo
-- `ritmo_seg_por_km`; RELATIVO usa `ritmo_ref_distancia` y `ritmo_delta_seg_por_km`). La coherencia la protege
-- el value object `Pace` en el dominio; el CHECK aquí es defensa en profundidad, no la única guarda.
CREATE TABLE planificacion.sesion (
    id                      UUID        NOT NULL,
    plan_id                 UUID        NOT NULL REFERENCES planificacion.plan_semanal (id),
    dia                     DATE        NOT NULL,
    ritmo_tipo              VARCHAR(20) NULL,
    ritmo_seg_por_km        INT         NULL,
    ritmo_ref_distancia     VARCHAR(10) NULL,
    ritmo_delta_seg_por_km  INT         NULL,
    CONSTRAINT sesion_pk PRIMARY KEY (id),
    CONSTRAINT sesion_ritmo_tipo_check CHECK (ritmo_tipo IS NULL OR ritmo_tipo IN ('ABSOLUTO', 'RELATIVO')),
    CONSTRAINT sesion_ritmo_ref_distancia_check
        CHECK (ritmo_ref_distancia IS NULL OR ritmo_ref_distancia IN ('5K', '10K', '21K', '42K'))
);

-- Soporta la carga eager del agregado completo en una sola query adicional (ADR-0008 D17, @EntityGraph).
CREATE INDEX sesion_plan_id_idx ON planificacion.sesion (plan_id);

-- Categoría RGPD: PII_PRIMARIA (categoría 1). `alumno_id` vincula la personalización a un alumno concreto;
-- `mensaje_al_alumno` es texto libre que puede contener datos personales. Sin caso de uso que la escriba
-- todavía (LAL-26); la tabla existe ya para que el agregado cargue completo desde el día 1.
CREATE TABLE planificacion.personalizacion (
    id                 UUID                     NOT NULL,
    plan_id            UUID                     NOT NULL,
    sesion_id          UUID                     NOT NULL REFERENCES planificacion.sesion (id),
    alumno_id          UUID                     NOT NULL,
    override           JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    mensaje_al_alumno  TEXT                     NULL,
    creado_en          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    modificado_en      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT personalizacion_pk PRIMARY KEY (id),
    -- Como mucho una personalización por alumno y sesión (ADR-0002 D9).
    CONSTRAINT personalizacion_plan_sesion_alumno_uk UNIQUE (plan_id, sesion_id, alumno_id)
);

CREATE INDEX personalizacion_alumno_idx ON planificacion.personalizacion (alumno_id);
