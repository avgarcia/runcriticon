-- Esquema del bounded context auditoria (ADR-0009, ADR-0013, persistencia.md §10). Aloja el
-- registro inmutable de auditoría y accesos sensibles; las tablas concretas se crean por feature
-- en Fase 1. Esta migración solo crea el contenedor (sin tablas, sin PII: categoría RGPD SIN_PII).
CREATE SCHEMA IF NOT EXISTS auditoria;
