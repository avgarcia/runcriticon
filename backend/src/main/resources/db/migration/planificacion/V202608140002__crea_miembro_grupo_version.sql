-- `miembro_grupo` pasa de alimentarse por deltas (LAL-94, un evento por alumno) a por snapshot completo
-- (`MembresiaDeGrupoCambiada`, LAL-25 prerrequisito): el reemplazo mayorista de las filas ALUMNO de un grupo
-- rompe la guarda de orden actual, que vive por fila en `miembro_grupo.last_processed_event_ts` -- si el grupo
-- se queda sin alumnos, se pierde la referencia contra la que comparar el siguiente evento.
--
-- Esta tabla guarda esa referencia aparte, por grupo: el `last_processed_event_ts` del último snapshot de
-- alumnos aplicado, exista o no ya alguna fila ALUMNO para ese grupo en `miembro_grupo`.
--
-- Categoría RGPD: SIN_PII. Solo el id del grupo y metadatos de versión del evento; ningún dato de una persona.

CREATE TABLE planificacion.miembro_grupo_version (
    grupo_id                 UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT miembro_grupo_version_pk PRIMARY KEY (grupo_id)
);
