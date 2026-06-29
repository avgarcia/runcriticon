-- RGPD: PII_PRIMARIA — token_hash vinculado a la identidad del usuario (ADR-0014, ADR-0003 D13).
-- Magic link de login (ADR-0003 D5): token de un solo uso, 15 minutos. Cambio aditivo y compatible
-- hacia atrás (deploy-then-migrate, ADR-0010 D11): tabla nueva que el código anterior ignora.
CREATE TABLE identidad.magic_link (
    id           UUID                     NOT NULL,
    usuario_id   UUID                     NOT NULL,
    club_id      UUID                     NOT NULL,
    token_hash   VARCHAR(255)             NOT NULL,
    emitido_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_en    TIMESTAMP WITH TIME ZONE NOT NULL,
    consumido_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT magic_link_pk PRIMARY KEY (id),
    CONSTRAINT magic_link_usuario_fk
        FOREIGN KEY (usuario_id) REFERENCES identidad.usuario(id)
);

-- Índice para la verificación del magic link (búsqueda por token_hash)
CREATE INDEX magic_link_token_hash_idx ON identidad.magic_link (token_hash);

-- Índice para consultas por usuario
CREATE INDEX magic_link_usuario_id_idx ON identidad.magic_link (usuario_id);
