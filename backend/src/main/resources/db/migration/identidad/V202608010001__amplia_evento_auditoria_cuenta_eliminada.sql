-- Amplía los tipos auditables de identidad con la supresión de una cuenta, que deja el único rastro de que el borrado
-- ocurrió: la fila del usuario ya no existe cuando se escribe el asiento.
-- Categoría RGPD: AUDITORIA_IDENTIDAD (categoría 2) — sin cambio de categoría.
-- Compatibilidad hacia atrás (deploy-then-migrate): solo AÑADE un valor al CHECK; el código anterior nunca inserta el
-- tipo nuevo, así que la versión previa sigue funcionando.
--
-- Pendiente conocido: estos asientos NO se anonimizan al ejercer el derecho de supresión. Tras el borrado sobreviven
-- `actor_id`, `sujeto_id`, `ip` y, en los asientos de rate-limiting, el `email_hash` de `metadata` (HMAC, no email en
-- claro). Son identificadores pseudónimos, que siguen siendo dato personal: el derecho de supresión queda satisfecho
-- solo parcialmente hasta que se implemente la anonimización de esta tabla.

ALTER TABLE identidad.evento_auditoria DROP CONSTRAINT evento_auditoria_tipo_check;

ALTER TABLE identidad.evento_auditoria ADD CONSTRAINT evento_auditoria_tipo_check CHECK (tipo IN (
    'INVITACION_EMITIDA', 'INVITACION_ACTIVADA', 'LOGIN_OK', 'LOGIN_FALLIDO',
    'MAGIC_LINK_EMITIDO', 'MAGIC_LINK_USADO', 'PASSWORD_CAMBIADA', 'PASSWORD_CADUCADA',
    'RESETEO_INICIADO', 'EMAIL_CAMBIO_INICIADO', 'EMAIL_CAMBIO_CONFIRMADO',
    'SESION_REVOCADA', 'CUENTA_DESACTIVADA', 'CUENTA_ELIMINADA',
    'MAGIC_LINK_RATE_LIMITED', 'RESETEO_RATE_LIMITED', 'INVITACION_RATE_LIMITED'
));
