-- Categoría RGPD: SIN_PII. No crea tabla; amplía `tag_key`, ya SIN_PII por la migración que la creó
-- (los ejes de la taxonomía no son datos de persona física).
--
-- `tipo` distingue si los valores del eje pueden llevar metadata de carrera (fecha + distancia).
-- `DEFAULT 'SIMPLE'` hace la columna compatible con la versión anterior de la app en un despliegue
-- deploy-then-migrate: una fila insertada por código que aún no conoce esta columna sigue siendo
-- válida.
ALTER TABLE club_taxonomia.tag_key
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SIMPLE';

ALTER TABLE club_taxonomia.tag_key
    ADD CONSTRAINT tag_key_tipo_check CHECK (tipo IN ('SIMPLE', 'CARRERA'));

-- El eje `objetivo` del club de bootstrap es el catálogo de carreras. Se corrige por id fijo, no por
-- nombre: el admin puede haberlo renombrado desde que se sembró. Si ese club o ese eje no existen
-- (entorno sin seed), el UPDATE afecta 0 filas.
UPDATE club_taxonomia.tag_key
   SET tipo = 'CARRERA'
 WHERE id = '00000000-0000-0000-0001-000000000003'::uuid;
