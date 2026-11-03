-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de cada tabla del esquema identidad, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. La categoría es la que ya declaraban las
-- migraciones que crearon cada tabla y, en las que tienen entidad JPA, su @RgpdCategory (el test exige que coincidan).

COMMENT ON TABLE identidad.usuario IS
    'RGPD: PII_PRIMARIA — email y nombre del usuario; borrado físico al suprimir la cuenta.';

COMMENT ON TABLE identidad.invitacion IS
    'RGPD: PII_PRIMARIA — invitación ligada al usuario invitado y a quien invitó; se borra con el invitado y se anula invitado_por al suprimir a quien invitó.';

COMMENT ON TABLE identidad.magic_link IS
    'RGPD: PII_PRIMARIA — token de acceso (hasheado) ligado a un usuario; se borra al suprimir la cuenta.';

COMMENT ON TABLE identidad.password_historico IS
    'RGPD: PII_PRIMARIA — hashes de las contraseñas anteriores de un usuario; se borran al suprimir la cuenta.';

COMMENT ON TABLE identidad.consentimiento IS
    'RGPD: PII_PRIMARIA — consentimiento de datos de salud de un usuario (art. 9.2.a), con su IP y user agent; se borra al suprimir la cuenta.';

COMMENT ON TABLE identidad.evento_auditoria IS
    'RGPD: AUDITORIA_IDENTIDAD — rastro de altas, bajas y cambios de credencial; al suprimir la cuenta se anonimiza (actor y sujeto a NULL, IP truncada), no se borra.';

COMMENT ON TABLE identidad.club IS
    'RGPD: SIN_PII — ficha del club; ningún dato de persona física.';

COMMENT ON TABLE identidad.evento_procesado IS
    'RGPD: SIN_PII — event_id ya procesados por cada listener del módulo; ningún dato de persona.';

-- Rollback: COMMENT ON TABLE identidad.<tabla> IS NULL; para usuario, invitacion, magic_link, password_historico,
-- consentimiento, evento_auditoria, club y evento_procesado.
