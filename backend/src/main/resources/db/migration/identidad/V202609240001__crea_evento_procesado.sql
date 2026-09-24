-- Tabla de idempotencia para los listeners del propio módulo (LAL-144, LAL-175 P2-3): los tres listeners de
-- email (invitación, magic link, reseteo) reaccionan a eventos internos del outbox de Spring Modulith, cuya
-- entrega es at-least-once (ADR-0007 D9); sin esta guarda, una reentrega reenvía el email. Calcada literal de
-- `planificacion.evento_procesado` (V202608130002), que ya sentó el precedente para los módulos que
-- necesitan idempotencia de listener sin proyección local asociada.

-- Categoría RGPD: SIN_PII. Solo registra qué `event_id` ha procesado ya cada listener del módulo; no
-- contiene datos de persona física.
CREATE TABLE identidad.evento_procesado (
    listener      VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_procesado_pk PRIMARY KEY (listener, event_id)
);

-- Soporta la limpieza periódica de filas de más de 30 días, alineada con la retención del outbox.
CREATE INDEX evento_procesado_processed_at_idx ON identidad.evento_procesado (processed_at);
