-- RGPD: PII_PRIMARIA — hash de contraseña vinculado a la identidad del usuario (ADR-0014, ADR-0003 D6)
-- Histórico para impedir reutilizar las últimas 5 contraseñas (política D6). Aditiva (deploy-then-migrate).
CREATE TABLE identidad.password_historico (
    id            UUID                     NOT NULL,
    usuario_id    UUID                     NOT NULL,
    club_id       UUID                     NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    creado_en     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT password_historico_pk PRIMARY KEY (id),
    CONSTRAINT password_historico_usuario_fk
        FOREIGN KEY (usuario_id) REFERENCES identidad.usuario(id)
);

-- Índice para recuperar las últimas N contraseñas del usuario (orden por fecha descendente)
CREATE INDEX password_historico_usuario_idx ON identidad.password_historico (usuario_id, creado_en DESC);
