-- Esquema del bounded context salud (ADR-0009, ADR-0013, persistencia.md §10). Aloja datos de
-- salud (categoría especial RGPD art. 9); las tablas concretas se crean por feature en Fase 1 y
-- deberán declarar su CategoriaRGPD. Esta migración solo crea el contenedor (sin tablas, sin PII).
CREATE SCHEMA IF NOT EXISTS salud;
