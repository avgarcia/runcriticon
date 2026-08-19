-- RGPD: AUDITORIA_AUTORIZACION (categoría 3) — asientos de auditoría de autorización (ADR-0009 D15-D17).
-- Al ejercer el derecho al olvido se ANONIMIZAN (actor_id/sujeto_id -> NULL), no se borran: es el rastro
-- de auditoría que debe sobrevivir a la persona que menciona (ADR-0014, patrón de borrado mixto). Lo
-- aplica AuditTrailAnonymizationListener al consumir AlumnoEliminado/EntrenadorEliminado.
CREATE TABLE auditoria.evento (
    id         UUID                     NOT NULL,
    club_id    UUID                     NOT NULL,
    tipo       VARCHAR(30)              NOT NULL CHECK (tipo IN ('ACCESO_DENEGADO', 'ACCESO_DATOS_SENSIBLES')),
    actor_id   UUID,
    sujeto_id  UUID,
    recurso    VARCHAR(120)             NOT NULL,
    motivo     TEXT,
    ts         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_pk PRIMARY KEY (id)
);

-- Consulta forense (GET /api/auditoria/eventos): siempre filtra por club_id, casi siempre ordena por ts.
CREATE INDEX evento_club_id_ts_idx ON auditoria.evento (club_id, ts DESC);

-- Soporta el UPDATE de anonimización de AuditTrailAnonymizationListener (WHERE actor_id = ? OR sujeto_id = ?).
CREATE INDEX evento_actor_id_idx ON auditoria.evento (actor_id);
CREATE INDEX evento_sujeto_id_idx ON auditoria.evento (sujeto_id);

-- Idempotencia de los listeners del módulo (ADR-0007 D9) — calcada literal de club_taxonomia.evento_procesado
-- (V202607300002), mismo precedente que ya siguió planificacion.evento_procesado (V202608130002).
CREATE TABLE auditoria.evento_procesado (
    listener      VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_procesado_pk PRIMARY KEY (listener, event_id)
);

CREATE INDEX evento_procesado_processed_at_idx ON auditoria.evento_procesado (processed_at);
