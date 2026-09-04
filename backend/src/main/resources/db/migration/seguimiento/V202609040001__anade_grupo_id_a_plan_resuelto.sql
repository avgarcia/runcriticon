-- LAL-116: el panel de alertas del entrenador necesita saber a qué grupo pertenece cada fila resuelta, para
-- acotar "solo mis grupos". `PlanPublicado.grupoId` ya lo trae el evento desde LAL-25, pero
-- `ResolvedPlanProjectionListener` lo descartaba — a partir de este PR lo persiste.
--
-- Aditiva y compatible hacia atrás (deploy-then-migrate, ADR-0010 D11): columna NULL, sin default forzado.
--
-- Categoría RGPD: sin cambio (PII_PRIMARIA, igual que el resto de `plan_resuelto_por_alumno`).
--
-- Sin backfill posible, mismo criterio que `V202609010001__anade_delta_ritmo_relativo.sql`:
-- `WeeklyPlan.publish` es terminal, `PlanPublicado` no se reemite y no guardamos su payload crudo. Las filas
-- ya proyectadas antes de este despliegue quedan con `grupo_id = NULL` hasta que el entrenador vuelva a
-- publicar el plan — `CoachAlertReaderJdbc` las excluye explícitamente, no las trata como "sin grupo válido
-- para todos los entrenadores".
ALTER TABLE seguimiento.plan_resuelto_por_alumno ADD COLUMN grupo_id UUID NULL;

-- Soporta el filtro de CoachAlertReaderJdbc: alumnos de los grupos que lleva un entrenador, dentro de una
-- ventana de días recientes.
CREATE INDEX plan_resuelto_club_grupo_dia_idx
    ON seguimiento.plan_resuelto_por_alumno (club_id, grupo_id, dia);
