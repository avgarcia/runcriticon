-- Reajuste de día por el alumno (LAL-33): el alumno mueve una sesión a otro día (≤ +7 días) o la marca como
-- saltada, con motivo (cansancio / molestias / imprevisto), sin depender de respuesta del entrenador.
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1) — el motivo MOLESTIAS es dato de salud (art. 9 RGPD), mismo
-- criterio que `reporte_sesion` (V202608240002). Retención: hasta baja + 30 días de gracia (ADR-0014 D10).
-- Borrado físico al consumir AlumnoEliminado, vía SeguimientoErasureJdbc.
--
-- Tabla de solo-superposición: NUNCA se escribe la proyección `plan_resuelto_por_alumno` (esa la escriben en
-- exclusiva los listeners de `planificacion`, ver ResolvedPlanProjectionJdbc). `ResolvedPlanReaderJdbc` hace
-- LEFT JOIN con esta tabla y calcula el día efectivo con COALESCE(dia_destino, p.dia) — el plan publicado
-- queda intacto (ADR-0002 D5, snapshot congelado).
--
-- `dia` es siempre el día PLANIFICADO (identidad estable de la sesión dentro del plan), igual que
-- `reporte_sesion.dia` — la traducción a día EFECTIVO solo ocurre en la ruta de lectura.
--
-- PK `(alumno_id, plan_id, dia)`, espejo de `reporte_sesion`: un reajuste nuevo sobre el mismo día PLANIFICADO
-- reemplaza (upsert), nunca duplica.
--
-- `plan_id` es el plan de la sesión de ESA fila, no un plan "de la operación": un alumno en dos grupos puede
-- tener sesiones de planes distintos el mismo día (ver el KDoc de ResolvedPlanReader.findWeek) — un
-- intercambio entre esas dos sesiones escribe dos filas, cada una con su propio plan_id.
--
-- `operacion_id` correlaciona las 2 filas que escribe un REEMPLAZAR/INTERCAMBIAR (o un deshacer): sin esto,
-- deshacer solo una fila de un intercambio dejaría la operación a medias.
CREATE TABLE seguimiento.reajuste_dia (
    alumno_id       UUID                     NOT NULL,
    plan_id         UUID                     NOT NULL,
    dia             DATE                     NOT NULL,
    club_id         UUID                     NOT NULL,
    operacion_id    UUID                     NOT NULL,
    accion          VARCHAR(10)              NOT NULL,
    dia_destino     DATE                     NULL,
    motivo          VARCHAR(20)              NOT NULL,
    mensaje         TEXT                     NULL,
    marca_dolor     BOOLEAN                  NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT reajuste_dia_pk PRIMARY KEY (alumno_id, plan_id, dia),
    CONSTRAINT reajuste_dia_accion_check
        CHECK (accion IN ('MOVIDA', 'SALTADA')),
    CONSTRAINT reajuste_dia_motivo_check
        CHECK (motivo IN ('CANSANCIO', 'MOLESTIAS', 'IMPREVISTO')),
    -- Defensa en profundidad del invariante que ya impone DayAdjustment.create en dominio: MOVIDA exige
    -- destino, SALTADA lo prohíbe.
    CONSTRAINT reajuste_dia_destino_coherente_check
        CHECK (
            (accion = 'MOVIDA' AND dia_destino IS NOT NULL)
            OR (accion = 'SALTADA' AND dia_destino IS NULL)
        )
);

-- Soporta el filtro de RescheduleDayCommand/ResolvedPlanReaderJdbc: siempre por club_id + alumno_id, casi
-- siempre además por dia_destino (para el overlay de lectura y la comprobación de conflicto).
CREATE INDEX reajuste_dia_club_alumno_destino_idx
    ON seguimiento.reajuste_dia (club_id, alumno_id, dia_destino);

-- Un solo día efectivo por destino: sin este índice, mover A(lunes)→miércoles y luego B(martes)→miércoles
-- dejaría dos sesiones reclamando el mismo día efectivo con solo una comprobación de aplicación detrás (el
-- caso de uso sí lo rechaza con 409 antes de llegar aquí, pero el invariante debe ser estructural, no solo de
-- aplicación).
CREATE UNIQUE INDEX reajuste_dia_destino_unico_idx
    ON seguimiento.reajuste_dia (alumno_id, dia_destino) WHERE accion = 'MOVIDA';

-- Soporta WithdrawDayAdjustmentCommand: deshacer una operación completa (REEMPLAZAR/INTERCAMBIAR escriben 2
-- filas con el mismo operacion_id) borra por esta clave, no por (alumno_id, plan_id, dia).
CREATE INDEX reajuste_dia_operacion_idx ON seguimiento.reajuste_dia (alumno_id, operacion_id);
