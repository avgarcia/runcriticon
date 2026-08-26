-- Proyección local de si un alumno tiene consentimiento vigente de datos de salud (LAL-128, PR2).
-- Categoría RGPD: SIN_PII — solo el hecho booleano "vigente" y la versión del texto, sin datos forenses
-- (ip, user_agent): esos viven en identidad.consentimiento, la fuente de verdad. Sin PII no hace falta
-- borrado físico por AlumnoEliminado más allá de mantener la tabla limpia (se hace igualmente, por
-- coherencia con el resto de la proyección de este módulo).
-- Alimentada por ConsentProjectionListener al consumir ConsentimientoConcedido/ConsentimientoRevocado.
CREATE TABLE seguimiento.consentimiento_alumno (
    alumno_id                UUID                     PRIMARY KEY,
    club_id                  UUID                     NOT NULL,
    vigente                  BOOLEAN                  NOT NULL,
    version_texto            VARCHAR(20)              NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Soporta el filtro de SubmitSessionReportCommand: siempre por club_id + alumno_id.
CREATE INDEX consentimiento_alumno_club_idx ON seguimiento.consentimiento_alumno (club_id, alumno_id);
