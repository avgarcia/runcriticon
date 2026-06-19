-- RGPD: PII_PRIMARIA — token_hash vinculado a la identidad del usuario (ADR-0014, ADR-0003 D13)
CREATE TABLE identidad.invitacion (
    id           UUID                     NOT NULL,
    usuario_id   UUID                     NOT NULL,
    club_id      UUID                     NOT NULL,
    token_hash   VARCHAR(255)             NOT NULL,
    emitida_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_en    TIMESTAMP WITH TIME ZONE NOT NULL,
    consumida_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT invitacion_pk PRIMARY KEY (id),
    CONSTRAINT invitacion_usuario_fk
        FOREIGN KEY (usuario_id) REFERENCES identidad.usuario(id)
);

-- Índice para la verificación del magic link (búsqueda por token_hash)
CREATE INDEX invitacion_token_hash_idx ON identidad.invitacion (token_hash);

-- Índice para la reinvitación (búsqueda por usuario)
CREATE INDEX invitacion_usuario_id_idx ON identidad.invitacion (usuario_id);
