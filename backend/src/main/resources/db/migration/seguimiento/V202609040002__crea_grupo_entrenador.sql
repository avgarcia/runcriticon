-- LAL-116: proyección propia de este módulo de qué entrenador lleva qué grupo, alimentada por
-- `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo` de `club_taxonomia` (vía
-- `CoachGroupProjectionListener`). Copia local del mismo hecho que `planificacion.miembro_grupo` ya
-- proyecta para sus propios fines — ADR-0007 prohíbe compartir esquema o FK entre módulos.
--
-- Solo el lado ENTRENADOR: a diferencia de `planificacion.miembro_grupo`, no hay fila ALUMNO aquí — la
-- pertenencia alumno↔grupo ya llega gratis vía `plan_resuelto_por_alumno.grupo_id` (V202609040001), así que
-- no hace falta duplicar el snapshot completo de `MembresiaDeGrupoCambiada`. Por el mismo motivo, sin tabla
-- de versión tipo `miembro_grupo_version`: esa solo hacía falta para la guarda de orden de un snapshot que
-- puede dejar el grupo vacío; aquí cada fila (grupo_id, entrenador_id) lleva su propia guarda de orden.
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1) — `entrenador_id` referencia a un entrenador concreto, misma
-- categoría que `planificacion.miembro_grupo`.
CREATE TABLE seguimiento.grupo_entrenador (
    grupo_id                 UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    entrenador_id            UUID                     NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT grupo_entrenador_pk PRIMARY KEY (grupo_id, entrenador_id)
);

-- Soporta CoachAlertReaderJdbc: "los grupos que lleva este entrenador en este club".
CREATE INDEX grupo_entrenador_club_entrenador_idx
    ON seguimiento.grupo_entrenador (club_id, entrenador_id);
