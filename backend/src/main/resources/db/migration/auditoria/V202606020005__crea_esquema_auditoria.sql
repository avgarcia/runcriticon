-- Esquema del bounded context auditoria (ADR-0009 D17, ADR-0004 D4, persistencia.md §1). Aloja el
-- registro inmutable de auditoría y accesos sensibles; las tablas concretas se crean por feature
-- en Fase 1. Esta migración solo crea el contenedor (sin tablas, sin PII: categoría RGPD SIN_PII).
CREATE SCHEMA IF NOT EXISTS auditoria;
