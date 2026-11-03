-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de cada tabla del esquema club_taxonomia, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. La categoría es la que ya declaraban las
-- migraciones que crearon cada tabla y, en las que tienen entidad JPA, su @RgpdCategory (el test exige que coincidan).
--
-- Una corrección: consentimiento_alumno pasa de SIN_PII a PII_PRIMARIA. Cada fila dice si un alumno concreto ha
-- consentido el tratamiento de sus datos de salud, y alumno_tag o grupo_entrenador, que también guardan solo una
-- referencia a la persona, ya son PII_PRIMARIA. El borrado no cambia: PersonErasureJdbc ya la borra.
--
-- sugerencia_fusion_grupo ya tenía comentario (V202609180001) sin categoría; se reescribe.

COMMENT ON TABLE club_taxonomia.persona IS
    'RGPD: PII_PRIMARIA — nombre y email de alumnos y entrenadores del club (proyección de identidad); borrado físico al suprimir la persona.';

COMMENT ON TABLE club_taxonomia.alumno_tag IS
    'RGPD: PII_PRIMARIA — clasificación de un alumno concreto; borrado físico al suprimirlo.';

COMMENT ON TABLE club_taxonomia.grupo_alumno_override IS
    'RGPD: PII_PRIMARIA — un alumno concreto incluido o excluido de un grupo a mano; borrado físico al suprimirlo.';

COMMENT ON TABLE club_taxonomia.grupo_entrenador IS
    'RGPD: PII_PRIMARIA — un entrenador concreto asignado a un grupo; borrado físico al suprimirlo.';

COMMENT ON TABLE club_taxonomia.consentimiento_alumno IS
    'RGPD: PII_PRIMARIA — si un alumno concreto tiene vigente el consentimiento de datos de salud (proyección de identidad.consentimiento); borrado físico al suprimirlo.';

COMMENT ON TABLE club_taxonomia.evento_auditoria IS
    'RGPD: AUDITORIA_IDENTIDAD — historial de cambios de clasificación del módulo; al suprimir la persona se anonimiza (actor y sujeto), no se borra.';

COMMENT ON TABLE club_taxonomia.tag_key IS
    'RGPD: SIN_PII — ejes de la taxonomía del club (nivel, objetivo, terreno…); no son datos de persona.';

COMMENT ON TABLE club_taxonomia.tag_value IS
    'RGPD: SIN_PII — valores de un eje de la taxonomía; describen el catálogo, no a una persona.';

COMMENT ON TABLE club_taxonomia.grupo IS
    'RGPD: SIN_PII — grupos del club; el nombre de un grupo no identifica a ninguna persona.';

COMMENT ON TABLE club_taxonomia.grupo_tag_requerido IS
    'RGPD: SIN_PII — valores de tag que definen un grupo; referencian el catálogo, no personas.';

COMMENT ON TABLE club_taxonomia.sugerencia_fusion_grupo IS
    'RGPD: SIN_PII — sugerencias calculadas de fusión de grupos (MergeSuggestionListener); solo ids de grupo.';

COMMENT ON TABLE club_taxonomia.persona_eliminada IS
    'RGPD: SIN_PII — identificador opaco de una persona ya suprimida, sin ningún otro atributo.';

COMMENT ON TABLE club_taxonomia.evento_procesado IS
    'RGPD: SIN_PII — event_id ya procesados por cada listener del módulo; ningún dato de persona.';

-- Rollback: restaurar el comentario de sugerencia_fusion_grupo que fijó V202609180001 y
-- COMMENT ON TABLE club_taxonomia.<tabla> IS NULL; para las otras doce tablas de este fichero.
