-- Asignación de entrenadores a un grupo (LAL-93, recorte sin Planificación): quién lleva qué grupo, la
-- relación que después gobernará quién puede publicarle un plan (AC2/AC3, hoy fuera de alcance porque el
-- módulo Planificación no existe todavía).
--
-- `club_id` fuera de la PK, mismo patrón que `grupo_alumno_override` (migración V202608050001): permite
-- filtrar `club_id = ?` en cada predicado como defensa anti-IDOR, sin indirección de subquery contra `grupo`.

-- Categoría RGPD: PII_PRIMARIA (categoría 1). Vincula un entrenador concreto a un grupo; misma categoría
-- que grupo_alumno_override y por el mismo motivo.
--
-- entrenador_id no lleva FK: referencia al módulo `identidad`, que vive en otro esquema (ADR-0004); mismo
-- patrón que alumno_id en grupo_alumno_override.
--
-- PersonErasureJdbc.erase() ya borra esta tabla desde el primer commit que la escribe (a diferencia de
-- grupo_alumno_override, que quedó como hueco documentado durante una migración): no hay ventana en la que
-- se pueda escribir una asignación que el borrado no alcance.
CREATE TABLE club_taxonomia.grupo_entrenador (
    grupo_id       UUID NOT NULL REFERENCES club_taxonomia.grupo (id),
    club_id        UUID NOT NULL,
    entrenador_id  UUID NOT NULL,
    CONSTRAINT grupo_entrenador_pk PRIMARY KEY (grupo_id, entrenador_id)
);

-- Consulta inversa: "qué grupos lleva este entrenador" (CoachDirectoryJdbc), que no puede apoyarse en la PK
-- porque su primera columna es grupo_id, no entrenador_id.
CREATE INDEX grupo_entrenador_club_entrenador_idx
    ON club_taxonomia.grupo_entrenador (club_id, entrenador_id);
