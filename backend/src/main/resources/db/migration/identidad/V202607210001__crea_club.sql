-- Tabla del club como entidad persistida (ADR-0006 D30).
-- Categoría RGPD: SIN_PII (categoría 0, ADR-0014) — la ficha del club no contiene datos de persona
-- física. El responsable del tratamiento sigue siendo Runcriticon S.L. (ADR-0014 D23).

CREATE TABLE identidad.club (
    id             UUID                     NOT NULL,
    nombre         VARCHAR(200)             NOT NULL,
    slug           VARCHAR(80),
    zona_horaria   VARCHAR(64)              NOT NULL DEFAULT 'Europe/Madrid',
    inicio_semana  VARCHAR(10)              NOT NULL DEFAULT 'LUNES',
    creado_en      TIMESTAMP WITH TIME ZONE NOT NULL,
    modificado_en  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT club_pk PRIMARY KEY (id),
    CONSTRAINT club_inicio_semana_check CHECK (inicio_semana IN ('LUNES', 'DOMINGO'))
);

-- Slug de solo lectura en el MVP (aún no se usa para enrutar por subdominio); único cuando se rellena,
-- sin obligar a inventar uno por fila (ADR-0006 D30, multi-club queda fuera del MVP).
CREATE UNIQUE INDEX club_slug_uk ON identidad.club (slug) WHERE slug IS NOT NULL;

-- Semilla defensiva: cubre tanto una instalación nueva (solo la fila canónica de bootstrap) como un
-- entorno que ya tenga usuarios con otros club_id (ej. tras un seed manual previo a esta migración).
-- ON CONFLICT hace la migración idempotente si se reaplica en un entorno que ya tiene alguna fila.
INSERT INTO identidad.club (id, nombre, creado_en, modificado_en)
SELECT DISTINCT u.club_id, 'Mi club', now(), now()
FROM identidad.usuario u
ON CONFLICT (id) DO NOTHING;

INSERT INTO identidad.club (id, nombre, creado_en, modificado_en)
VALUES ('00000000-0000-0000-0000-000000000001', 'Mi club', now(), now())
ON CONFLICT (id) DO NOTHING;

-- Solo ahora, con toda fila de club destino ya sembrada, se puede acoplar la FK sin romper ningún
-- entorno existente (deploy-then-migrate, ADR-0010 D11).
ALTER TABLE identidad.usuario
    ADD CONSTRAINT usuario_club_fk FOREIGN KEY (club_id) REFERENCES identidad.club (id);
