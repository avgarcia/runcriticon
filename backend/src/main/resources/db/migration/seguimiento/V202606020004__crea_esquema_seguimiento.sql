-- Esquema del bounded context seguimiento (ADR-0004 D4, persistencia.md §1). Aloja datos de
-- salud (categoría especial RGPD art. 9 — ADR-0014); las tablas concretas se crean por feature en
-- Fase 1 y deberán declarar su CategoriaRGPD. Esta migración solo crea el contenedor (sin tablas, sin PII).
CREATE SCHEMA IF NOT EXISTS seguimiento;
