-- Declara en el catálogo la categoría RGPD (ADR-0014 D5) de cada tabla del esquema seguimiento, con el formato
-- 'RGPD: <CATEGORÍA> — <motivo>' que exige RgpdTableCategoryIntegrationTest. El módulo no tiene entidades JPA, así que
-- la categoría de sus tablas de datos de salud solo constaba en comentarios `--` que nada comprobaba.
--
-- Una corrección: consentimiento_alumno pasa de SIN_PII a PII_PRIMARIA. Cada fila dice si un alumno concreto ha
-- consentido el tratamiento de sus datos de salud, y las demás proyecciones que solo guardan una referencia a la persona
-- ya son PII_PRIMARIA. El borrado no cambia: el listener de supresión del módulo ya la borra.

COMMENT ON TABLE seguimiento.reporte_sesion IS
    'RGPD: PII_PRIMARIA — reporte de sesión de un alumno, con datos de salud (art. 9: sensaciones, dolor); borrado físico al suprimirlo.';

COMMENT ON TABLE seguimiento.marca_alumno IS
    'RGPD: PII_PRIMARIA — marcas personales de un alumno, dato de salud (art. 9); borrado físico al suprimirlo.';

COMMENT ON TABLE seguimiento.reajuste_dia IS
    'RGPD: PII_PRIMARIA — reajustes de día de un alumno; los motivos MOLESTIAS y LESION son dato de salud (art. 9); borrado físico al suprimirlo.';

COMMENT ON TABLE seguimiento.plan_resuelto_por_alumno IS
    'RGPD: PII_PRIMARIA — plan semanal resuelto para un alumno concreto; borrado físico al suprimirlo.';

COMMENT ON TABLE seguimiento.consentimiento_alumno IS
    'RGPD: PII_PRIMARIA — si un alumno concreto tiene vigente el consentimiento de datos de salud (proyección de identidad.consentimiento); borrado físico al suprimirlo.';

COMMENT ON TABLE seguimiento.grupo_entrenador IS
    'RGPD: PII_PRIMARIA — entrenadores asignados a cada grupo (proyección de club_taxonomia).';

COMMENT ON TABLE seguimiento.grupo_entrenador_version IS
    'RGPD: PII_PRIMARIA — versión de la asignación de entrenadores a cada grupo; entrenador_id referencia a un entrenador.';

COMMENT ON TABLE seguimiento.evento_procesado IS
    'RGPD: SIN_PII — event_id ya procesados por cada listener del módulo; ningún dato de persona.';

-- Rollback: COMMENT ON TABLE seguimiento.<tabla> IS NULL; para las ocho tablas de este fichero.
