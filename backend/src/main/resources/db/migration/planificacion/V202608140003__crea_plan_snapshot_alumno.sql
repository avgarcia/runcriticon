-- RGPD: PII_PRIMARIA (referencia a alumno; borrado físico vía PlanificacionDeletionListener, LAL-25)
CREATE TABLE planificacion.plan_snapshot_alumno (
    plan_id      UUID                     NOT NULL REFERENCES planificacion.plan_semanal (id),
    club_id      UUID                     NOT NULL,
    alumno_id    UUID                     NOT NULL,
    congelado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT plan_snapshot_alumno_pk PRIMARY KEY (plan_id, alumno_id)
);
CREATE INDEX plan_snapshot_alumno_club_alumno_idx ON planificacion.plan_snapshot_alumno (club_id, alumno_id);
