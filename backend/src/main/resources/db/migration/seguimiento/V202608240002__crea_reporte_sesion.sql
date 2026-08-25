-- El reporte de sesión del alumno (LAL-30): lo que registra sobre una sesión ya ejecutada. Primer agregado
-- propio con escritura del módulo seguimiento (hasta ahora solo tenía la proyección de solo lectura de
-- LAL-29).
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1) — incluye datos de salud del art. 9 RGPD (valoración de
-- sensaciones, marca de dolor). ADR-0014 D5 nombra esta tabla literalmente como ejemplo de categoría 1.
-- Retención: hasta baja + 30 días de gracia (ADR-0014 D10). Borrado FÍSICO al consumir AlumnoEliminado
-- (ADR-0014 D6), vía SeguimientoErasureJdbc — no anonimización: ADR-0004 D16 propone anonimizar esta misma
-- tabla, en contradicción directa con ADR-0014 D5/D6; se sigue ADR-0014 (es el ADR de RGPD) con el mismo
-- criterio que ya aplica el código mergeado de PlanificacionErasureJdbc a personalizacion/plan_snapshot_alumno,
-- dos de las tres tablas que ADR-0004 D16 nombra para anonimizar. Revisión de ADR-0004 D16 abierta aparte.
--
-- `descripcion_dolor` se crea ya pero no se rellena en esta historia: es un dato médico derivado (ubicación e
-- intensidad del dolor) con pregunta jurídica abierta (docs/arquitectura/rgpd-en-modulos.md §9, pendiente del
-- módulo) — mismo patrón que `mensaje_al_alumno` en LAL-29 (columna creada, sin escribir, para no migrar de
-- nuevo cuando llegue).
--
-- PK `(alumno_id, plan_id, dia)`, espejo de `plan_resuelto_por_alumno`: reportar dos veces el mismo día es
-- EDITAR (upsert), no duplicar — es el estado "editando reporte enviado" del spec 07. Y conserva la misma
-- propiedad multi-grupo: dos planes de grupos distintos que resuelven el mismo día son dos reportes
-- distintos, sin colisión.
CREATE TABLE seguimiento.reporte_sesion (
    alumno_id          UUID                     NOT NULL,
    plan_id            UUID                     NOT NULL,
    dia                DATE                     NOT NULL,
    club_id            UUID                     NOT NULL,
    estado             VARCHAR(20)              NOT NULL,
    valoracion         SMALLINT                 NULL,
    motivo             VARCHAR(20)              NULL,
    notas              TEXT                     NULL,
    marca_dolor        BOOLEAN                  NOT NULL DEFAULT FALSE,
    descripcion_dolor  TEXT                     NULL,
    reportado_en       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT reporte_sesion_pk PRIMARY KEY (alumno_id, plan_id, dia),
    CONSTRAINT reporte_sesion_estado_check
        CHECK (estado IN ('HECHO', 'PARCIAL', 'NO_HECHO')),
    CONSTRAINT reporte_sesion_motivo_check
        CHECK (motivo IS NULL OR motivo IN
            ('CANSANCIO', 'TRABAJO', 'VIAJE', 'ENFERMEDAD', 'SIN_TIEMPO', 'MOLESTIAS', 'OTRA')),
    CONSTRAINT reporte_sesion_valoracion_check
        CHECK (valoracion IS NULL OR valoracion BETWEEN 1 AND 5),
    -- Defensa en profundidad de los invariantes del glosario que ya impone SessionReport.create en dominio:
    -- valoración obligatoria si HECHO/PARCIAL y ausente si NO_HECHO; motivo obligatorio si NO_HECHO y ausente
    -- en el resto. Una fila que viole esto solo puede llegar por fuera de la aplicación.
    CONSTRAINT reporte_sesion_valoracion_coherente_check
        CHECK (
            (estado IN ('HECHO', 'PARCIAL') AND valoracion IS NOT NULL)
            OR (estado = 'NO_HECHO' AND valoracion IS NULL)
        ),
    CONSTRAINT reporte_sesion_motivo_coherente_check
        CHECK (
            (estado = 'NO_HECHO' AND motivo IS NOT NULL)
            OR (estado IN ('HECHO', 'PARCIAL') AND motivo IS NULL)
        )
);

-- Soporta el filtro de GetMyWeekQuery/SubmitSessionReportCommand: siempre por club_id + alumno_id, casi
-- siempre además por dia o rango de dia.
CREATE INDEX reporte_sesion_club_alumno_dia_idx ON seguimiento.reporte_sesion (club_id, alumno_id, dia);
