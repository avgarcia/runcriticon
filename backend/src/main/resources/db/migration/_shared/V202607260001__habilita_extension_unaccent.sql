-- Habilita la extensión `unaccent` de PostgreSQL (transliteración de acentos). Es infraestructura de la BD, no de un
-- módulo concreto (una extensión se instala una vez por base de datos) — de ahí que viva en `_shared/` y no en la
-- migración de `club_taxonomia`, aunque hoy sea su único consumidor (índice único de `tag_key`/`tag_value`).
--
-- `unaccent(text)` es STABLE (resuelve el diccionario por defecto vía search_path), así que no sirve dentro de un
-- índice de expresión, que exige IMMUTABLE. La forma de dos argumentos `unaccent(regdictionary, text)` sí es
-- IMMUTABLE al recibir el diccionario ya resuelto; la función wrapper que la usa vive en la migración de
-- `club_taxonomia` (`club_taxonomia.normalizar_etiqueta`).
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;