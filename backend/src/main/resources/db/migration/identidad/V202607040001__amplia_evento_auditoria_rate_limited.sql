-- Amplía los tipos auditables de identidad con los eventos de rate-limiting (ADR-0003 D12, LAL-35).
-- Categoría RGPD: AUDITORIA_IDENTIDAD (categoría 2, ADR-0014) — sin cambio de categoría; se sigue
-- usando la columna `metadata` (JSONB) ya existente para el email_hash y la IP del origen.
-- Compatibilidad hacia atrás (deploy-then-migrate, ADR-0010 D11): solo AÑADE valores al CHECK; el
-- código anterior nunca inserta los tipos nuevos, así que la versión previa sigue funcionando.

ALTER TABLE identidad.evento_auditoria DROP CONSTRAINT evento_auditoria_tipo_check;

ALTER TABLE identidad.evento_auditoria ADD CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN (
    'INVITACION_EMITIDA', 'INVITACION_ACTIVADA', 'LOGIN_OK', 'LOGIN_FALLIDO',
    'MAGIC_LINK_EMITIDO', 'MAGIC_LINK_USADO', 'PASSWORD_CAMBIADA', 'PASSWORD_CADUCADA',
    'RESETEO_INICIADO', 'EMAIL_CAMBIO_INICIADO', 'EMAIL_CAMBIO_CONFIRMADO',
    'SESION_REVOCADA', 'CUENTA_DESACTIVADA',
    'MAGIC_LINK_RATE_LIMITED', 'RESETEO_RATE_LIMITED', 'INVITACION_RATE_LIMITED'
));
