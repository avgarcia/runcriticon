-- La migración que creó el outbox (V202606010001) no declaró la categoría RGPD de la tabla, a diferencia del resto.
-- Editarla rompería su checksum en las bases ya migradas, así que el comentario va en una migración nueva.
--
-- Categoría RGPD: OUTBOX (categoría 4, ADR-0014 D5). Guarda el payload completo de los eventos publicados, que puede
-- contener datos personales. Se purga a los 30 días con `EventPublicationRetentionJob` (ADR-0014 D10).
COMMENT ON TABLE public.event_publication IS
    'RGPD categoría 4 (outbox, ADR-0014 D5): payload completo de los eventos publicados; retención 30 días (EventPublicationRetentionJob).';
