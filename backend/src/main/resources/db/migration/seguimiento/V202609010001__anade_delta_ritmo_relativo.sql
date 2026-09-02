-- LAL-32: cierra el hueco que `V202608240001` dejó a propósito — el ritmo relativo se resuelve contra la
-- marca real del alumno (ADR-0002 D8). Falta el delta firmado que el entrenador fijó al crear la sesión:
-- `ritmo_referencia_distancia`/`ritmo_falta_marca` ya identifican la distancia, pero sin el delta no hay con
-- qué calcular `marca.paceSecondsPerKm() + delta` en el listener de recálculo (`MarkPaceRecalculationListener`)
-- sin volver a leer el evento `PlanPublicado`, que puede llevar mucho tiempo fuera del outbox.
--
-- Aditiva y compatible hacia atrás (deploy-then-migrate, ADR-0010 D11): columna NULL, sin default forzado, no
-- rompe ninguna fila existente ni ningún despliegue en curso que todavía no conozca la columna.
--
-- Categoría RGPD: sin cambio (PII_PRIMARIA, igual que el resto de `plan_resuelto_por_alumno`).
--
-- Sin backfill posible para las filas `RELATIVO` ya proyectadas: `WeeklyPlan.publish` es terminal
-- (ADR-0007/ADR-0008), el evento `PlanPublicado` que las originó no se reemite, y no guardamos su payload
-- crudo en ningún sitio. Esas filas quedan en "falta marca" (columna `ritmo_falta_marca` ya rellena desde
-- V202608240001) hasta que el entrenador vuelva a publicar el plan — momento en el que `ResolvedPlanProjectionListener`
-- las reescribe con el delta del evento nuevo. Documentado también en el KDoc de `ResolvedPace.Relative`.
ALTER TABLE seguimiento.plan_resuelto_por_alumno ADD COLUMN ritmo_delta_seg_por_km INT NULL;

-- Mismo criterio que `sesion_ritmo_*_check` de `planificacion.sesion`: un ritmo calculado que llegara a
-- persistirse en 0 o negativo sería un ritmo sin sentido físico (m:ss/km). `resolveRelativePace`/
-- `RESOLVE_RELATIVE_PACE_SQL` ya aplican un suelo de 1 s/km; este CHECK es la red de seguridad en la BD.
ALTER TABLE seguimiento.plan_resuelto_por_alumno ADD CONSTRAINT plan_resuelto_ritmo_calculado_positivo_check
    CHECK (ritmo_calculado_seg_por_km IS NULL OR ritmo_calculado_seg_por_km > 0);
