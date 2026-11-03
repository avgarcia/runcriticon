-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de cada tabla del esquema auditoria, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. La migración que creó las dos tablas
-- (V202608190001) solo declaraba la de evento; evento_procesado es SIN_PII, igual que en el resto de módulos.

COMMENT ON TABLE auditoria.evento IS
    'RGPD: AUDITORIA_AUTORIZACION — asientos de decisiones de autorización y accesos sensibles; al suprimir la persona se anonimizan (actor y sujeto), no se borran.';

COMMENT ON TABLE auditoria.evento_procesado IS
    'RGPD: SIN_PII — event_id ya procesados por cada listener del módulo; ningún dato de persona.';

-- Rollback: COMMENT ON TABLE auditoria.evento IS NULL; COMMENT ON TABLE auditoria.evento_procesado IS NULL;
