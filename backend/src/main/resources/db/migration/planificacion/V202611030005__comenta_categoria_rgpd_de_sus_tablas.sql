-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de cada tabla del esquema planificacion, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. La categoría es la que ya declaraban las
-- migraciones que crearon cada tabla; el módulo no tiene entidades JPA, así que hasta ahora nada la comprobaba.

COMMENT ON TABLE planificacion.plan_semanal IS
    'RGPD: PII_PRIMARIA — plan semanal de un grupo; entrenador_id vincula el plan a quien lo creó.';

COMMENT ON TABLE planificacion.personalizacion IS
    'RGPD: PII_PRIMARIA — personalización de una sesión para un alumno concreto; borrado físico al suprimirlo.';

COMMENT ON TABLE planificacion.plan_snapshot_alumno IS
    'RGPD: PII_PRIMARIA — alumnos que formaban el grupo al publicarse un plan; borrado físico al suprimirlos.';

COMMENT ON TABLE planificacion.miembro_grupo IS
    'RGPD: PII_PRIMARIA — alumnos y entrenadores miembros de cada grupo (proyección de club_taxonomia).';

COMMENT ON TABLE planificacion.miembro_grupo_entrenador_version IS
    'RGPD: PII_PRIMARIA — versión de la asignación de entrenadores a cada grupo; persona_id referencia a un entrenador.';

COMMENT ON TABLE planificacion.sesion IS
    'RGPD: SIN_PII — contenido de entrenamiento de un plan (día, ritmo objetivo, notas); no referencia a ninguna persona.';

COMMENT ON TABLE planificacion.miembro_grupo_version IS
    'RGPD: SIN_PII — versión de la membresía de cada grupo; solo el id del grupo y metadatos del evento.';

COMMENT ON TABLE planificacion.evento_procesado IS
    'RGPD: SIN_PII — event_id ya procesados por cada listener del módulo; ningún dato de persona.';

-- Rollback: COMMENT ON TABLE planificacion.<tabla> IS NULL; para las ocho tablas de este fichero.
