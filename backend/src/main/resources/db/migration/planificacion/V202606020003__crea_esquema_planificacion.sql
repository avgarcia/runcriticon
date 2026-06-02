-- Esquema del bounded context planificacion (ADR-0009, persistencia.md §10). Cada módulo aísla
-- sus tablas en su propio schema de PostgreSQL; las tablas concretas se crean por feature en
-- Fase 1. Esta migración solo crea el contenedor (sin tablas, sin PII: categoría RGPD SIN_PII).
CREATE SCHEMA IF NOT EXISTS planificacion;
