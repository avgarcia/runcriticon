-- Marcadores explícitos para los valores de la taxonomía que son dato de salud (art. 9 RGPD, ADR-0014 D16), en vez de
-- reconocerlos por su nombre: renombrarlos ya no apaga la auditoría de lectura, la guarda de consentimiento ni la
-- retirada al revocar.
--   dato_salud     el valor es un dato de salud: las lecturas que lo exponen se auditan, asignarlo exige
--                  consentimiento vigente del alumno y se retira cuando lo revoca.
--   rol_reservado  papel fijo que el sistema da a un valor. Hoy solo LESION: el que asigna LesionDeclaradaListener
--                  cuando el alumno avisa de lesión. Uno por club como mucho, y siempre es dato de salud.
-- Categoría RGPD de la tabla: SIN_PII, sin cambios — los marcadores describen el catálogo, no a una persona.
-- Compatible con deploy-then-migrate (ADR-0010 D11): columnas nuevas con valor por defecto o nulables.
ALTER TABLE club_taxonomia.tag_value
    ADD COLUMN dato_salud    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN rol_reservado VARCHAR(20) NULL,
    ADD CONSTRAINT tag_value_rol_reservado_ck CHECK (rol_reservado IS NULL OR rol_reservado IN ('LESION')),
    ADD CONSTRAINT tag_value_rol_reservado_dato_salud_ck CHECK (rol_reservado IS NULL OR dato_salud);

CREATE UNIQUE INDEX tag_value_club_rol_reservado_uk
    ON club_taxonomia.tag_value (club_id, rol_reservado)
    WHERE rol_reservado IS NOT NULL;

-- Valores sembrados del eje estado (V202607300001), por id y no por nombre: lesión y post-parto son datos de salud
-- (post-parto revela embarazo o parto), y lesión es además el rol LESION.
UPDATE club_taxonomia.tag_value
SET dato_salud = TRUE, rol_reservado = 'LESION'
WHERE id = '00000000-0000-0000-0002-00000000000f';

UPDATE club_taxonomia.tag_value
SET dato_salud = TRUE
WHERE id = '00000000-0000-0000-0002-000000000010';
