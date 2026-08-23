-- Auditoría LOCAL del módulo club_taxonomia (LAL-87 AC3): historial de qué tags tenía un alumno antes/después de
-- cada cambio de clasificación. Distinta del bounded context `auditoria` (auditoría de autorización) — mismo patrón
-- que ya usa `identidad.evento_auditoria`, generalizado a categoría 2 de ADR-0014 D5/D6 (revisión previa).
--
-- Categoría RGPD: AUDITORIA_IDENTIDAD (categoría 2, ADR-0014). Al ejercer el derecho de supresión, se anonimiza
-- (`actor_id`/`sujeto_id` a NULL), no se borra: es el rastro de auditoría que debe sobrevivir a la persona que
-- menciona. Pendiente de programar junto al resto del borrado mixto de este módulo (mismo seguimiento que la
-- categoría 1 de `persona`, ver V202607300002).
--
-- A diferencia de `identidad.evento_auditoria`, esta tabla SÍ lleva `club_id`: `club_taxonomia` es club-scoped y el
-- adaptador (`ClubTaxonomiaAuditTrailImpl`) escribe con `@AuthScope(Scope.CLUB)`, no `@NoAuthScope` — aquí siempre
-- hay un `Principal` autenticado en el punto de escritura.

CREATE TABLE club_taxonomia.evento_auditoria (
    id        UUID                     NOT NULL,
    club_id   UUID                     NOT NULL,
    tipo      VARCHAR(40)              NOT NULL,
    actor_id  UUID,
    sujeto_id UUID,
    ts        TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata  JSONB,
    CONSTRAINT evento_auditoria_pk PRIMARY KEY (id),
    CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN ('TAGS_ALUMNO_ACTUALIZADOS'))
);

-- Consulta de eventos por club (pantalla de admin, si llega a construirse) y por alumno afectado.
CREATE INDEX evento_auditoria_club_id_idx ON club_taxonomia.evento_auditoria (club_id);
CREATE INDEX evento_auditoria_sujeto_id_idx ON club_taxonomia.evento_auditoria (sujeto_id);
CREATE INDEX evento_auditoria_ts_idx ON club_taxonomia.evento_auditoria (ts);
