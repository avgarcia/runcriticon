-- Tablas de la taxonomía del club: `tag_key` (ejes: nivel, objetivo, terreno…), `tag_value` (valores permitidos de
-- cada eje) y `alumno_tag` (asignación N:M de un alumno a valores). Dominio: Taxonomy/TagKey/TagValue.

-- Función IMMUTABLE propia: `unaccent(text)` es STABLE (resuelve el diccionario por defecto vía search_path) y
-- PostgreSQL rechaza funciones STABLE dentro de un índice de expresión. La forma de dos argumentos
-- `unaccent(regdictionary, text)` sí es IMMUTABLE al recibir el diccionario ya resuelto como OID, sin depender del
-- search_path en tiempo de consulta. Replica el orden de TagLabel.normalized (trim → sin diacríticos → minúsculas);
-- las divergencias conocidas entre esta función y TagLabel.normalized están documentadas en TagLabel.kt — el índice
-- es la red de seguridad ante condiciones de carrera, no la primera línea de defensa (la impone el agregado).
CREATE OR REPLACE FUNCTION club_taxonomia.normalizar_etiqueta(etiqueta TEXT)
RETURNS TEXT AS $$
    SELECT lower(public.unaccent('public.unaccent'::regdictionary, btrim(etiqueta)))
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Categoría RGPD: SIN_PII. Los ejes de la taxonomía (nivel, objetivo, terreno…) no son datos de persona física.
CREATE TABLE club_taxonomia.tag_key (
    id            UUID                     NOT NULL,
    club_id       UUID                     NOT NULL,
    nombre        VARCHAR(40)              NOT NULL,
    archivado_en  TIMESTAMP WITH TIME ZONE,
    creado_en     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT tag_key_pk PRIMARY KEY (id)
);

CREATE INDEX tag_key_club_id_idx ON club_taxonomia.tag_key (club_id);

-- Unicidad por club ignorando mayúsculas/acentos/espacios (TagLabel.normalized); solo compite entre keys activas
-- (WHERE archivado_en IS NULL) — un nombre archivado se libera para reutilizarlo (Taxonomy.addKey/reactivateKey).
CREATE UNIQUE INDEX tag_key_club_nombre_uk
    ON club_taxonomia.tag_key (club_id, club_taxonomia.normalizar_etiqueta(nombre))
    WHERE archivado_en IS NULL;

-- Categoría RGPD: SIN_PII. Los valores de un eje (5K, principiante…) no son datos de persona física; la metadata de
-- una carrera (fecha + distancia) tampoco identifica a nadie.
CREATE TABLE club_taxonomia.tag_value (
    id            UUID                     NOT NULL,
    tag_key_id    UUID                     NOT NULL REFERENCES club_taxonomia.tag_key (id),
    club_id       UUID                     NOT NULL,
    nombre        VARCHAR(60)              NOT NULL,
    metadata      JSONB                    NOT NULL DEFAULT '{"tipo": "Empty"}'::jsonb,
    archivado_en  TIMESTAMP WITH TIME ZONE,
    creado_en     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT tag_value_pk PRIMARY KEY (id),
    -- Defensa en profundidad: la forma exacta de cada variante (fecha/distancia de 'Race') la valida el dominio, no
    -- este CHECK — solo comprueba que el discriminante 'tipo' sea uno de los conocidos (TagValueMetadata sellada).
    CONSTRAINT tag_value_metadata_tipo_check CHECK (metadata ? 'tipo' AND metadata ->> 'tipo' IN ('Empty', 'Race'))
);

CREATE INDEX tag_value_club_id_idx ON club_taxonomia.tag_value (club_id);
CREATE INDEX tag_value_tag_key_id_idx ON club_taxonomia.tag_value (tag_key_id);

-- Unicidad dentro del mismo eje (no del club entero: "5K" puede repetirse como valor de "objetivo" y de "distancia
-- habitual" si el club tuviera dos ejes así), ignorando mayúsculas/acentos/espacios, solo entre valores activos.
CREATE UNIQUE INDEX tag_value_key_nombre_uk
    ON club_taxonomia.tag_value (tag_key_id, club_taxonomia.normalizar_etiqueta(nombre))
    WHERE archivado_en IS NULL;

-- Categoría RGPD: PII_PRIMARIA (categoría 1). Vincula a un alumno concreto con su clasificación; requiere borrado
-- físico al consumir el evento AlumnoEliminado (StudentDeletionListener, pendiente de programar); esta tabla no
-- recibe datos reales hasta que exista la proyección de personas del club y el caso de uso de asignación de tags.
-- `alumno_id` no lleva FK: referencia al módulo `identidad`, que vive en otro esquema; la coherencia la da el evento
-- consumido por la proyección local, no una FK cruzada.
CREATE TABLE club_taxonomia.alumno_tag (
    club_id       UUID                     NOT NULL,
    alumno_id     UUID                     NOT NULL,
    tag_value_id  UUID                     NOT NULL REFERENCES club_taxonomia.tag_value (id),
    creado_en     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT alumno_tag_pk PRIMARY KEY (alumno_id, tag_value_id)
);

CREATE INDEX alumno_tag_club_id_idx ON club_taxonomia.alumno_tag (club_id);

-- Soporta el CTE de resolución de membresía de grupo (cumplen_tags), que agrupa por alumno los tag_value que tiene
-- cada uno.
CREATE INDEX alumno_tag_tag_value_alumno_idx ON club_taxonomia.alumno_tag (tag_value_id, alumno_id);