-- Fontanería de consumo de eventos de este módulo (LAL-114): la proyección local de pertenencia a grupo
-- (`miembro_grupo`, alimentada por los cuatro eventos de LAL-94 de `club_taxonomia`) y la tabla de
-- idempotencia de sus listeners (`evento_procesado`) — calcada literal de `club_taxonomia.evento_procesado`
-- (V202607300002), que ya sentó este precedente para los módulos que consumen eventos de otro.
--
-- Idioma de las columnas: castellano para las de negocio (`rol`), inglés para las técnicas de la fontanería
-- de eventos (`event_id`, `processed_at`, `last_processed_event_id`, `last_processed_event_ts`), mismo
-- criterio que la tabla que copia.

-- Categoría RGPD: PII_PRIMARIA (categoría 1). `persona_id` referencia a un alumno o entrenador concreto —
-- misma categoría que `club_taxonomia.grupo_entrenador`/`grupo_alumno_override`, que son la fuente real de
-- estos eventos.
--
-- `rol` distingue ALUMNO de ENTRENADOR únicamente para lectura/depuración: el MVP fija un rol único por
-- usuario (ADR-0003), así que `(grupo_id, persona_id)` ya es única por sí sola sin necesitar `rol` en la PK.
CREATE TABLE planificacion.miembro_grupo (
    grupo_id                 UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    persona_id               UUID                     NOT NULL,
    rol                      VARCHAR(20)              NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT miembro_grupo_pk PRIMARY KEY (grupo_id, persona_id),
    CONSTRAINT miembro_grupo_rol_check CHECK (rol IN ('ALUMNO', 'ENTRENADOR'))
);

-- Consulta inversa: "a qué grupos pertenece esta persona" / comprobación puntual "es esta persona miembro de
-- este grupo" (`CoachGroupLookup`), que no puede apoyarse en la PK porque su primera columna es grupo_id.
CREATE INDEX miembro_grupo_club_persona_idx ON planificacion.miembro_grupo (club_id, persona_id);

-- Categoría RGPD: SIN_PII. Solo registra qué `event_id` ha procesado ya cada listener del módulo; no
-- contiene datos de persona física.
CREATE TABLE planificacion.evento_procesado (
    listener      VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_procesado_pk PRIMARY KEY (listener, event_id)
);

-- Soporta la limpieza periódica de filas de más de 30 días, alineada con la retención del outbox.
CREATE INDEX evento_procesado_processed_at_idx ON planificacion.evento_procesado (processed_at);
