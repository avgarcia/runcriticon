-- Modelo de grupo del club (ADR-0002 D3+D4): `grupo` + la consulta sobre tags que define su membresía
-- (`grupo_tag_requerido`) + las excepciones manuales que la sobrescriben (`grupo_alumno_override`). Dominio:
-- Group/GroupId/GroupName.
--
-- `club_id` se añade a `grupo_tag_requerido` y `grupo_alumno_override` fuera de la PK, mismo patrón que
-- `alumno_tag` (migración V202607260002): permite que la resolución de membresía filtre por `club_id = ?` en cada
-- predicado como defensa en profundidad anti-IDOR, sin indirección de subquery contra `grupo`.

-- Categoría RGPD: SIN_PII. El nombre de un grupo no identifica a ninguna persona física.
CREATE TABLE club_taxonomia.grupo (
    id         UUID                     NOT NULL,
    club_id    UUID                     NOT NULL,
    -- Longitud 80: sin spec ni wireframe que la fije (`constructor-grupos.html` no tiene `maxlength`); escalado
    -- razonado desde tag_key.nombre (40) y tag_value.nombre (60).
    nombre     VARCHAR(80)              NOT NULL,
    creado_en  TIMESTAMPTZ              NOT NULL DEFAULT now(),
    CONSTRAINT grupo_pk PRIMARY KEY (id)
);

CREATE INDEX grupo_club_id_idx ON club_taxonomia.grupo (club_id);

-- Categoría RGPD: SIN_PII. Cada fila referencia un tag_value, no una persona.
--
-- La "consulta" del grupo es este conjunto de filas (ADR-0002 D3): un alumno pertenece si tiene TODOS los
-- tag_value_id requeridos. Solo AND en el MVP -- sin OR ni negación (aplazado, ver Notas de D3).
CREATE TABLE club_taxonomia.grupo_tag_requerido (
    grupo_id      UUID NOT NULL REFERENCES club_taxonomia.grupo (id),
    club_id       UUID NOT NULL,
    tag_value_id  UUID NOT NULL REFERENCES club_taxonomia.tag_value (id),
    -- La PK ya es el índice (grupo_id, tag_value_id) que exige el AC de LAL-90 para el JOIN/HAVING de la
    -- resolución -- no se añade un índice adicional redundante.
    CONSTRAINT grupo_tag_requerido_pk PRIMARY KEY (grupo_id, tag_value_id)
);

-- Categoría RGPD: PII_PRIMARIA (categoría 1). Vincula un alumno concreto a un grupo por excepción manual (D4);
-- misma categoría que alumno_tag y por el mismo motivo.
--
-- Hueco conocido, no resuelto aquí: PersonErasureJdbc.erase() (borrado RGPD de una persona, LAL-105) borra hoy
-- `persona` y `alumno_tag`, pero no conoce esta tabla porque no existía todavía. En cuanto LAL-92 introduzca el
-- caso de uso que escribe overrides, erase() deberá ampliarse con
-- `DELETE FROM club_taxonomia.grupo_alumno_override WHERE alumno_id = ?` -- si no, un alumno con `incluido = TRUE`
-- que se borre sobrevive indefinidamente en la resolución de membresía vía la CTE `incluidos`. Esta migración crea
-- la tabla vacía: ningún caso de uso escribe en ella todavía.
--
-- alumno_id no lleva FK: referencia al módulo `identidad`, que vive en otro esquema (ADR-0004); mismo patrón que
-- alumno_tag.
CREATE TABLE club_taxonomia.grupo_alumno_override (
    grupo_id   UUID    NOT NULL REFERENCES club_taxonomia.grupo (id),
    club_id    UUID    NOT NULL,
    alumno_id  UUID    NOT NULL,
    incluido   BOOLEAN NOT NULL,
    CONSTRAINT grupo_alumno_override_pk PRIMARY KEY (grupo_id, alumno_id)
);

-- La PK ya cubre (grupo_id, alumno_id); este índice añade `incluido` a la derecha para que las CTEs `incluidos`/
-- `excluidos` de la resolución (ADR-0002 D3, "índices imprescindibles") sean index-only scans sobre el filtro
-- `WHERE grupo_id = ? AND incluido = ?`.
CREATE INDEX grupo_alumno_override_grupo_alumno_incluido_idx
    ON club_taxonomia.grupo_alumno_override (grupo_id, alumno_id, incluido);
