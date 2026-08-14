-- Índice inverso sobre grupo_tag_requerido, para responder "¿qué grupos usan este tag_value_id en su filtro?"
-- (recálculo de membresía por cambio de tags de un alumno). La PK actual es (grupo_id, tag_value_id), que no
-- sirve para buscar por valor: sin este índice, `WHERE tag_value_id = ANY (?)` sería un escaneo completo.
--
-- Aditivo puro, compatible deploy-then-migrate (ADR-0010 D11): no toca filas ni columnas existentes.

CREATE INDEX grupo_tag_requerido_club_tag_value_idx
    ON club_taxonomia.grupo_tag_requerido (club_id, tag_value_id);
