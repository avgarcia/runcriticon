-- Amplía los tipos auditables de identidad con la concesión y revocación de consentimiento (LAL-128).
-- Categoría RGPD: AUDITORIA_IDENTIDAD (categoría 2) — sin cambio de categoría.
-- Compatibilidad hacia atrás (deploy-then-migrate, ADR-0010 D11): solo AÑADE valores al CHECK; el
-- código anterior nunca inserta los tipos nuevos, así que la versión previa sigue funcionando.

ALTER TABLE identidad.evento_auditoria DROP CONSTRAINT evento_auditoria_tipo_check;

ALTER TABLE identidad.evento_auditoria ADD CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN (
    'INVITACION_EMITIDA', 'INVITACION_ACTIVADA', 'LOGIN_OK', 'LOGIN_FALLIDO',
    'MAGIC_LINK_EMITIDO', 'MAGIC_LINK_USADO', 'PASSWORD_CAMBIADA', 'PASSWORD_CADUCADA',
    'RESETEO_INICIADO', 'EMAIL_CAMBIO_INICIADO', 'EMAIL_CAMBIO_CONFIRMADO',
    'SESION_REVOCADA', 'CUENTA_DESACTIVADA', 'CUENTA_ELIMINADA',
    'MAGIC_LINK_RATE_LIMITED', 'RESETEO_RATE_LIMITED', 'INVITACION_RATE_LIMITED',
    'CONSENTIMIENTO_CONCEDIDO', 'CONSENTIMIENTO_REVOCADO'
));
