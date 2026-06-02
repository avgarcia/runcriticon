-- Tabla de usuarios del módulo identidad (ADR-0003 D2).
-- Categoría RGPD: PII_PRIMARIA (categoría 1, ADR-0014) — email y nombre son datos personales.
-- El borrado por derecho de supresión se trata según el patrón de borrado mixto de ADR-0014.

CREATE TABLE identidad.usuario (
    id                UUID                     NOT NULL,
    club_id           UUID                     NOT NULL,
    email             VARCHAR(320)             NOT NULL,
    email_normalizado VARCHAR(320)             NOT NULL,
    nombre            VARCHAR(200)             NOT NULL,
    rol               VARCHAR(20)              NOT NULL,
    password_hash     VARCHAR(255),
    estado            VARCHAR(20)              NOT NULL,
    creado_en         TIMESTAMP WITH TIME ZONE NOT NULL,
    modificado_en     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT usuario_pk PRIMARY KEY (id),
    CONSTRAINT usuario_rol_check CHECK (rol IN ('ADMIN', 'ENTRENADOR', 'ALUMNO')),
    CONSTRAINT usuario_estado_check CHECK (estado IN ('INVITADO', 'ACTIVO', 'DESACTIVADO'))
);

-- Unicidad del email por club, normalizado a minúsculas (ADR-0003 D2).
CREATE UNIQUE INDEX usuario_club_email_uk ON identidad.usuario (club_id, email_normalizado);
