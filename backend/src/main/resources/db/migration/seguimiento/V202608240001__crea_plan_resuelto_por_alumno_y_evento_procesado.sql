-- Arranque real del módulo (LAL-29): la proyección local que resuelve para cada alumno lo que ve en su
-- vista "hoy" a partir de `PlanPublicado` (planificacion), y la tabla de idempotencia de sus listeners —
-- calcada literal de `planificacion.evento_procesado` (V202608130002), que ya sentó este precedente.
--
-- Idioma de las columnas: castellano para las de negocio (`dia`, `mensaje_al_alumno`, `ritmo_*`), inglés
-- para las técnicas de la fontanería de eventos (`event_id`, `processed_at`, `last_processed_event_id`,
-- `last_processed_event_ts`), mismo criterio que el resto de módulos.

-- Categoría RGPD: PII_PRIMARIA (categoría 1). `alumno_id` referencia a una persona concreta; un plan de
-- entrenamiento no es dato de salud del art. 9 (eso llega con las marcas del alumno, LAL-31, y el reporte de
-- sensaciones, LAL-30) — mismo criterio que `planificacion.plan_snapshot_alumno`.
--
-- Clave primaria `(alumno_id, plan_id, dia)`, no `(alumno_id, dia)`: los grupos son consultas sobre tags, no
-- excluyentes — un alumno puede pertenecer a dos grupos cuyos entrenadores publican, cada uno, un plan para
-- la misma semana. Con `UNIQUE (alumno_id, dia)` esa colisión sería una violación de constraint dentro del
-- listener del outbox, que no tiene reintentos con backoff (ADR-0007) y acabaría en la DLQ. El listener debe
-- ser incapaz de fallar; el desempate de qué fila se muestra se hace al leer (`ResolvedPlanReaderJdbc`).
--
-- `sesion_resuelta` JSONB guarda tipo, volumen y notas de la sesión ya resuelta para el alumno — el ritmo va
-- aparte, en columnas planas, porque LAL-32 necesitará filtrar por `ritmo_referencia_distancia` para
-- recalcular tras una marca nueva del alumno.
--
-- `mensaje_al_alumno` y `es_personalizada` se crean ya, siempre `NULL`/`false`: no hay evento de
-- personalización todavía (`SesionPersonalizada` llega con LAL-26), pero crear las columnas ahora evita una
-- migración adicional cuando exista. `es_personalizada` es uso interno (alertas, métricas futuras) — nunca se
-- expone al alumno en el contrato REST.
CREATE TABLE seguimiento.plan_resuelto_por_alumno (
    alumno_id                   UUID                     NOT NULL,
    plan_id                     UUID                     NOT NULL,
    club_id                     UUID                     NOT NULL,
    dia                         DATE                     NOT NULL,
    sesion_resuelta             JSONB                    NOT NULL,
    ritmo_tipo_origen           VARCHAR(20)              NULL,
    ritmo_calculado_seg_por_km  INT                      NULL,
    ritmo_referencia_distancia  VARCHAR(10)              NULL,
    ritmo_falta_marca           VARCHAR(10)              NULL,
    mensaje_al_alumno           TEXT                     NULL,
    es_personalizada            BOOLEAN                  NOT NULL DEFAULT FALSE,
    last_processed_event_id     UUID                     NOT NULL,
    last_processed_event_ts     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT plan_resuelto_por_alumno_pk PRIMARY KEY (alumno_id, plan_id, dia),
    CONSTRAINT plan_resuelto_ritmo_tipo_origen_check
        CHECK (ritmo_tipo_origen IS NULL OR ritmo_tipo_origen IN ('ABSOLUTO', 'RELATIVO')),
    CONSTRAINT plan_resuelto_ritmo_referencia_distancia_check
        CHECK (ritmo_referencia_distancia IS NULL OR ritmo_referencia_distancia IN ('5K', '10K', '21K', '42K')),
    CONSTRAINT plan_resuelto_ritmo_falta_marca_check
        CHECK (ritmo_falta_marca IS NULL OR ritmo_falta_marca IN ('5K', '10K', '21K', '42K'))
);

-- Soporta `GetMyWeekQuery`: siempre filtra por club_id + alumno_id, casi siempre por rango de dia.
CREATE INDEX plan_resuelto_club_alumno_dia_idx
    ON seguimiento.plan_resuelto_por_alumno (club_id, alumno_id, dia);

-- Categoría RGPD: SIN_PII. Solo registra qué `event_id` ha procesado ya cada listener del módulo; no
-- contiene datos de persona física.
CREATE TABLE seguimiento.evento_procesado (
    listener      VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_procesado_pk PRIMARY KEY (listener, event_id)
);

-- Soporta la limpieza periódica de filas de más de 30 días, alineada con la retención del outbox.
CREATE INDEX evento_procesado_processed_at_idx ON seguimiento.evento_procesado (processed_at);
