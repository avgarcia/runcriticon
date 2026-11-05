-- Estado BAJA de la cuenta (ADR-0003 D2): el admin decidió suprimirla y corren los 30 días de gracia de
-- ADR-0014 D14. Un job diario suprime las cuentas con la gracia vencida (ADR-0017 D9).
--
-- RGPD: PII_PRIMARIA (categoría ya vigente de la tabla); las dos columnas nuevas son metadatos de la
-- cuenta y desaparecen con ella al suprimirla.
--
-- Compatibilidad hacia atrás (deploy-then-migrate, ADR-0010 D11): solo añade columnas nulables y
-- valores a los CHECK. El código anterior nunca escribe BAJA ni las columnas nuevas, así que cumple la
-- restricción de coherencia (estado distinto de BAJA y las dos columnas nulas).

ALTER TABLE identidad.usuario DROP CONSTRAINT usuario_estado_check;

ALTER TABLE identidad.usuario ADD CONSTRAINT usuario_estado_check
    CHECK (estado IN ('INVITADO', 'ACTIVO', 'DESACTIVADO', 'BAJA'));

ALTER TABLE identidad.usuario
    ADD COLUMN baja_en       TIMESTAMPTZ NULL,
    ADD COLUMN estado_previo VARCHAR(20) NULL;

-- Cancelar la baja devuelve la cuenta a estado_previo, así que nunca puede ser BAJA.
ALTER TABLE identidad.usuario ADD CONSTRAINT usuario_estado_previo_check
    CHECK (estado_previo IN ('INVITADO', 'ACTIVO', 'DESACTIVADO'));

-- Cada una de las dos columnas tiene valor si y solo si la cuenta está en BAJA.
ALTER TABLE identidad.usuario ADD CONSTRAINT usuario_baja_coherente_check
    CHECK (((estado = 'BAJA') = (baja_en IS NOT NULL)) AND ((estado = 'BAJA') = (estado_previo IS NOT NULL)));

-- El job diario solo recorre las cuentas en baja.
CREATE INDEX usuario_baja_en_idx ON identidad.usuario (baja_en) WHERE estado = 'BAJA';

COMMENT ON COLUMN identidad.usuario.baja_en IS
    'Cuándo el admin marcó la baja; la cuenta se suprime 30 días después. No nulo si y solo si estado = BAJA.';
COMMENT ON COLUMN identidad.usuario.estado_previo IS
    'Estado al que vuelve la cuenta si el admin cancela la baja. No nulo si y solo si estado = BAJA.';

-- Tipos auditables nuevos: marcar y cancelar la baja. Mantiene la lista completa vigente.
ALTER TABLE identidad.evento_auditoria DROP CONSTRAINT evento_auditoria_tipo_check;

ALTER TABLE identidad.evento_auditoria ADD CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN (
    'INVITACION_EMITIDA', 'INVITACION_ACTIVADA', 'LOGIN_OK', 'LOGIN_FALLIDO',
    'MAGIC_LINK_EMITIDO', 'MAGIC_LINK_USADO', 'PASSWORD_CAMBIADA', 'PASSWORD_CADUCADA',
    'RESETEO_INICIADO', 'EMAIL_CAMBIO_INICIADO', 'EMAIL_CAMBIO_CONFIRMADO',
    'SESION_REVOCADA', 'CUENTA_DESACTIVADA', 'CUENTA_ELIMINADA',
    'MAGIC_LINK_RATE_LIMITED', 'RESETEO_RATE_LIMITED', 'INVITACION_RATE_LIMITED',
    'CONSENTIMIENTO_CONCEDIDO', 'CONSENTIMIENTO_REVOCADO',
    'BAJA_MARCADA', 'BAJA_CANCELADA'
));

-- Rollback: devolver antes cada cuenta en baja a su estado previo (UPDATE identidad.usuario SET
-- estado = estado_previo, baja_en = NULL, estado_previo = NULL WHERE estado = 'BAJA'); DROP INDEX
-- identidad.usuario_baja_en_idx; DROP CONSTRAINT usuario_baja_coherente_check y
-- usuario_estado_previo_check; DROP COLUMN baja_en y estado_previo; y restaurar usuario_estado_check y
-- evento_auditoria_tipo_check con las listas de V202606030002 y V202608250002. Los asientos BAJA_MARCADA
-- y BAJA_CANCELADA no caben en el CHECK anterior: hay que borrarlos, y eso no es reversible.
