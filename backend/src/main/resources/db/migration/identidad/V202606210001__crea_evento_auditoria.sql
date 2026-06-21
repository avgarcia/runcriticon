-- Auditoría de eventos de identidad (ADR-0003 D15).
-- Categoría RGPD: AUDITORIA_IDENTIDAD (categoría 2, ADR-0014). Retención 12 meses (purga posterior).
-- Auditoría LOCAL del módulo identidad; distinta del bounded context `auditoria` (auditoría de autorización).

CREATE TABLE identidad.evento_auditoria (
    id        UUID                     NOT NULL,
    tipo      VARCHAR(40)              NOT NULL,
    actor_id  UUID,
    sujeto_id UUID,
    ts        TIMESTAMP WITH TIME ZONE NOT NULL,
    ip        INET,
    metadata  JSONB,
    CONSTRAINT evento_auditoria_pk PRIMARY KEY (id),
    CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN (
        'INVITACION_EMITIDA', 'INVITACION_ACTIVADA', 'LOGIN_OK', 'LOGIN_FALLIDO',
        'MAGIC_LINK_EMITIDO', 'MAGIC_LINK_USADO', 'PASSWORD_CAMBIADA', 'PASSWORD_CADUCADA',
        'RESETEO_INICIADO', 'EMAIL_CAMBIO_INICIADO', 'EMAIL_CAMBIO_CONFIRMADO',
        'SESION_REVOCADA', 'CUENTA_DESACTIVADA'
    ))
);

-- Consulta de eventos por usuario afectado (pantalla de admin) y purga por antigüedad (retención).
CREATE INDEX evento_auditoria_sujeto_id_idx ON identidad.evento_auditoria (sujeto_id);
CREATE INDEX evento_auditoria_ts_idx ON identidad.evento_auditoria (ts);
