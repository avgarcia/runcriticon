-- Marca de agua de las filas ENTRENADOR de `miembro_grupo`: el último evento aplicado a cada par (grupo,
-- entrenador), que sobrevive al borrado de la fila. La guarda de orden vivía solo en la propia fila, y
-- `EntrenadorEliminadoDeGrupo` la borra: a partir de ahí no quedaba nada contra lo que comparar, y un
-- `EntrenadorAsignadoAGrupo` anterior entregado después (reentrega tras un fallo, o los dos listeners en paralelo)
-- volvía a insertarla. El entrenador recuperaba el permiso de publicar y personalizar los planes del grupo
-- (`CoachGroupLookup`, ADR-0009). Los alumnos ya tienen la suya, por grupo, en `miembro_grupo_version`.
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1). `persona_id` referencia a un entrenador concreto, igual que en
-- `miembro_grupo`; la supresión de la persona borra las dos tablas.
CREATE TABLE planificacion.miembro_grupo_entrenador_version (
    grupo_id                 UUID                     NOT NULL,
    persona_id               UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT miembro_grupo_entrenador_version_pk PRIMARY KEY (grupo_id, persona_id)
);

-- La supresión de la persona borra por `persona_id`, que no es la primera columna de la PK.
CREATE INDEX miembro_grupo_entrenador_version_persona_idx
    ON planificacion.miembro_grupo_entrenador_version (persona_id);

-- Cada fila ENTRENADOR existente ya lleva el último evento que la escribió: la marca de agua arranca desde ahí.
-- Las bajas anteriores a esta migración no dejaron rastro y no se pueden recuperar.
INSERT INTO planificacion.miembro_grupo_entrenador_version
    (grupo_id, persona_id, club_id, last_processed_event_id, last_processed_event_ts)
SELECT grupo_id, persona_id, club_id, last_processed_event_id, last_processed_event_ts
FROM planificacion.miembro_grupo
WHERE rol = 'ENTRENADOR';
