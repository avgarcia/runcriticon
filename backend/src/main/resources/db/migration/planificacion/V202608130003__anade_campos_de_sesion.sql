-- Editor de sesión (LAL-24): la sesión pasa de ser un hueco vacío del agregado a llevar tipo, volumen
-- (distancia o tiempo, nunca los dos) y notas. `planificacion.sesion` está vacía en todo entorno —
-- LAL-114 creó la tabla pero ningún caso de uso escribía en ella todavía — así que las columnas nuevas
-- van `NOT NULL` sin `DEFAULT`: si algún entorno tuviera filas de verdad, la migración debe fallar en vez
-- de inventar un tipo o un volumen que nadie pidió.
--
-- Categoría RGPD: sigue SIN_PII. `notas` describe el entrenamiento (tipo, series, sensaciones esperadas),
-- no a ningún alumno — el texto dirigido a una persona concreta vive en `personalizacion.mensaje_al_alumno`
-- (PII_PRIMARIA, ya declarada en la migración anterior).

ALTER TABLE planificacion.sesion
    ADD COLUMN tipo           VARCHAR(20) NOT NULL,
    ADD COLUMN volumen_tipo   VARCHAR(20) NULL,
    ADD COLUMN volumen_metros INT         NULL,
    ADD COLUMN volumen_minutos INT        NULL,
    ADD COLUMN notas          TEXT        NULL;

-- Catálogo cerrado del glosario (docs/glosario.md §Planificación) — los 10 tipos de sesión del MVP.
ALTER TABLE planificacion.sesion
    ADD CONSTRAINT sesion_tipo_check CHECK (
        tipo IN (
            'RODAJE', 'SERIES', 'TEMPO', 'TIRADA_LARGA', 'FARTLEK',
            'CUESTAS', 'PROGRESIVO', 'FUERZA_CROSS', 'COMPETICION', 'DESCANSO'
        )
    );

-- `volumen_tipo` decide cuál de las otras dos columnas aplica (mismo patrón que `ritmo_tipo`); DESCANSO no
-- lleva volumen. Defensa en profundidad: la coherencia real la protege `SessionVolume` en el dominio.
ALTER TABLE planificacion.sesion
    ADD CONSTRAINT sesion_volumen_check CHECK (
        (volumen_tipo IS NULL AND volumen_metros IS NULL AND volumen_minutos IS NULL)
        OR (volumen_tipo = 'DISTANCE' AND volumen_metros IS NOT NULL AND volumen_minutos IS NULL)
        OR (volumen_tipo = 'DURATION' AND volumen_minutos IS NOT NULL AND volumen_metros IS NULL)
    );

-- Una sesión por día y plan (LAL-24, decisión 2): las dos maquetas de referencia pintan siete huecos, uno
-- por día de la semana — permitir duplicados en silencio produciría una pantalla ambigua sin que ningún
-- criterio de aceptación lo pida.
ALTER TABLE planificacion.sesion
    ADD CONSTRAINT sesion_plan_dia_uk UNIQUE (plan_id, dia);
