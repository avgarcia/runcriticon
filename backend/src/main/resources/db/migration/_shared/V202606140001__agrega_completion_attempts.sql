-- Actualización del outbox de Spring Modulith 2.0.x (ADR-0007 D6).
-- Spring Modulith 2.0.x añadió completion_attempts para el seguimiento de reintentos
-- de entrega de eventos. La migración original (V202606010001) no la incluía.
-- Categoría RGPD: OUTBOX (sin PII directa, heredada de ADR-0014).

ALTER TABLE event_publication
    ADD COLUMN IF NOT EXISTS completion_attempts INT NOT NULL DEFAULT 0;
