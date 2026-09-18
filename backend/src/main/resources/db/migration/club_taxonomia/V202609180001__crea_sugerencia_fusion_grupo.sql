-- Sugerencias de fusión de micro-grupos o de grupos casi duplicados (LAL-96).
-- Categoría RGPD: SIN_PII. Solo referencia ids de grupo, ninguna persona física.

CREATE TABLE club_taxonomia.sugerencia_fusion_grupo (
    club_id       UUID        NOT NULL,
    grupo_id_a    UUID        NOT NULL REFERENCES club_taxonomia.grupo (id),
    grupo_id_b    UUID        NOT NULL REFERENCES club_taxonomia.grupo (id),
    tipo          TEXT        NOT NULL CHECK (tipo IN ('MICRO', 'DUPLICADO')),
    calculado_en  TIMESTAMPTZ NOT NULL,
    descartada_en TIMESTAMPTZ,
    PRIMARY KEY (club_id, grupo_id_a, grupo_id_b, tipo)
);

-- En MICRO, grupo_id_a = grupo_id_b (sugerencia de un único grupo, ver MergeSuggestion.kt). En DUPLICADO,
-- grupo_id_a < grupo_id_b siempre: el par se guarda una sola vez, en orden canónico.
COMMENT ON TABLE club_taxonomia.sugerencia_fusion_grupo IS
    'Sugerencias calculadas de fusión de grupos (LAL-96); recalculadas por MergeSuggestionListener a partir de MembresiaDeGrupoCambiada.';
