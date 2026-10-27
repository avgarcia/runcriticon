-- Proyección local de si un alumno tiene consentimiento vigente de datos de salud (ADR-0014 D16/D18), para que
-- clubtaxonomia pueda decidir sobre el tag "estado = lesión" sin llamar a identidad (ADR-0007): retirarlo cuando el
-- alumno revoca y negarse a asignarlo a mano a quien no ha consentido.
-- Categoría RGPD: SIN_PII — solo el hecho booleano "vigente"; la fuente de verdad, con sus datos forenses (ip,
-- user_agent), es identidad.consentimiento. Se borra igualmente con la supresión del alumno (PersonErasureJdbc), por
-- coherencia con el resto de proyecciones del módulo.
-- Alimentada por HealthConsentListener al consumir ConsentimientoConcedido/ConsentimientoRevocado.
CREATE TABLE club_taxonomia.consentimiento_alumno (
    alumno_id                UUID                     PRIMARY KEY,
    club_id                  UUID                     NOT NULL,
    vigente                  BOOLEAN                  NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Soporta la guarda de asignación: siempre por club_id + alumno_id.
CREATE INDEX consentimiento_alumno_club_idx ON club_taxonomia.consentimiento_alumno (club_id, alumno_id);
