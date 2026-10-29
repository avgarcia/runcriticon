-- Marca de agua de `grupo_entrenador`: el último evento aplicado a cada par (grupo, entrenador), que sobrevive al
-- borrado de la fila. La guarda de orden vivía solo en la propia fila, y `EntrenadorEliminadoDeGrupo` la borra: a
-- partir de ahí no quedaba nada contra lo que comparar, y un `EntrenadorAsignadoAGrupo` anterior entregado después
-- (reentrega tras un fallo, o los dos listeners en paralelo) volvía a insertarla. El entrenador recuperaba el grupo
-- en el panel de alertas. Mismo patrón que `planificacion.miembro_grupo_version` para los alumnos.
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1). `entrenador_id` referencia a un entrenador concreto, igual que en
-- `grupo_entrenador`; la supresión del entrenador borra las dos tablas.
CREATE TABLE seguimiento.grupo_entrenador_version (
    grupo_id                 UUID                     NOT NULL,
    entrenador_id            UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT grupo_entrenador_version_pk PRIMARY KEY (grupo_id, entrenador_id)
);

-- La supresión del entrenador borra por `entrenador_id`, que no es la primera columna de la PK.
CREATE INDEX grupo_entrenador_version_entrenador_idx
    ON seguimiento.grupo_entrenador_version (entrenador_id);

-- Cada fila existente ya lleva el último evento que la escribió: la marca de agua arranca desde ahí. Las bajas
-- anteriores a esta migración no dejaron rastro y no se pueden recuperar.
INSERT INTO seguimiento.grupo_entrenador_version
    (grupo_id, entrenador_id, club_id, last_processed_event_id, last_processed_event_ts)
SELECT grupo_id, entrenador_id, club_id, last_processed_event_id, last_processed_event_ts
FROM seguimiento.grupo_entrenador;
