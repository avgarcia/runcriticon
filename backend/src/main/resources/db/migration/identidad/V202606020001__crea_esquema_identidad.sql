-- Esquema del bounded context identidad (ADR-0004 D4, persistencia.md §1). Cada módulo aísla
-- sus tablas en su propio schema de PostgreSQL; las tablas concretas se crean por feature en
-- Fase 1. Esta migración solo crea el contenedor (sin tablas, sin PII: categoría RGPD SIN_PII).
CREATE SCHEMA IF NOT EXISTS identidad;
