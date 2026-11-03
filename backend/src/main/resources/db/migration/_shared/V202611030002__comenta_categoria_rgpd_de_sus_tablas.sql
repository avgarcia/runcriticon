-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de las tablas compartidas del esquema public, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. Hasta ahora la categoría solo constaba en
-- comentarios `--` de las migraciones que las crearon, y nada comprobaba que existiera.
--
-- Las tablas de Spring Session pasan a PII_PRIMARIA: su migración las describía como «datos operativos de sesión», que
-- no es ninguna categoría, y cada sesión está indexada por el UUID del usuario (PRINCIPAL_NAME). Mismo criterio que las
-- proyecciones que solo guardan una referencia a la persona; el borrado ya existe (SessionRevoker al suprimir la
-- cuenta).
--
-- `event_publication` ya tenía comentario (V202611030001) en otro formato; se reescribe.

COMMENT ON TABLE public.event_publication IS
    'RGPD: OUTBOX — payload completo de los eventos publicados; retención de 30 días para los completados (EventPublicationRetentionJob).';

COMMENT ON TABLE public.spring_session IS
    'RGPD: PII_PRIMARIA — sesión de un usuario, indexada por su UUID (PRINCIPAL_NAME); se borra al expirar, al cerrar sesión y al suprimir la cuenta (SessionRevoker).';

COMMENT ON TABLE public.spring_session_attributes IS
    'RGPD: PII_PRIMARIA — SecurityContext serializado de una sesión (UUID de usuario y club, rol); se borra con su sesión.';

-- Rollback: restaurar el comentario de event_publication que fijó V202611030001 y
-- COMMENT ON TABLE public.spring_session IS NULL; COMMENT ON TABLE public.spring_session_attributes IS NULL;
