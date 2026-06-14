-- Columnas adicionales de Spring Modulith 2.0.x en event_publication (ADR-0007 D6).
-- status: estado del ciclo de vida (PUBLISHED/PROCESSING/COMPLETED/FAILED/RESUBMITTED).
-- last_resubmission_date: marca temporal del último reintento de entrega (nullable).
-- Categoría RGPD: OUTBOX (sin PII directa, ADR-0014).

ALTER TABLE event_publication
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE event_publication
    ADD COLUMN IF NOT EXISTS last_resubmission_date TIMESTAMP WITH TIME ZONE;
