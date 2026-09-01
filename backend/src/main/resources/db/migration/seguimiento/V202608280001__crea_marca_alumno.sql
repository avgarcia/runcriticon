-- La marca del alumno (LAL-31, ADR-0002 D7): el mejor tiempo del corredor en una distancia estándar.
-- Agregado pequeño, sin histórico en MVP: cada actualización sobreescribe la anterior (PK compuesta).
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1) -- dato de salud sensible (art. 9 RGPD), citado por ADR-0014 D5
-- bajo el nombre `seguimiento.marca`; se usa aquí el nombre canónico de ADR-0002 D7, `marca_alumno`.
-- Retención: hasta baja + 30 días de gracia (ADR-0014 D10). Borrado FÍSICO al consumir AlumnoEliminado, vía
-- SeguimientoErasureJdbc.
--
-- club_id: no aparece en la tabla resumen de ADR-0002 D7, pero toda tabla de dominio de este backend filtra
-- por club_id -- igual que sus tablas hermanas reporte_sesion/plan_resuelto_por_alumno del mismo módulo.
--
-- Privacidad fuerte (ADR-0002 D7): solo el alumno lee y escribe. Sin FK ni vista agregada para
-- entrenador/admin -- la barrera vive en AuthorizationMatrix (sin fila ADMIN/ENTRENADOR sobre Resource.MARCA).
CREATE TABLE seguimiento.marca_alumno (
    alumno_id       UUID                     NOT NULL,
    distancia       VARCHAR(4)               NOT NULL,
    tiempo_segundos INT                      NOT NULL,
    club_id         UUID                     NOT NULL,
    modificado_en   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT marca_alumno_pk PRIMARY KEY (alumno_id, distancia),
    CONSTRAINT marca_alumno_distancia_check CHECK (distancia IN ('5K', '10K', '21K', '42K')),
    CONSTRAINT marca_alumno_tiempo_positivo_check CHECK (tiempo_segundos > 0)
);

CREATE INDEX marca_alumno_club_idx ON seguimiento.marca_alumno (club_id, alumno_id);
