package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.LocalDate

/**
 * Escritura del reporte de sesión del alumno (LAL-30). Puerto aparte de [ResolvedPlanReader]: el reporte es
 * su propio agregado, no una vista de la proyección — [ResolvedPlanReader.findWeek]/[findDay] ya lo traen por
 * `LEFT JOIN` para lectura, este puerto es solo para escribirlo.
 */
interface SessionReportRepository {
    /**
     * Crea o reemplaza el reporte de [studentId] para el plan [planId] en [day] — envío idempotente, es la
     * misma operación tanto la primera vez como al editar uno ya enviado (spec 07, estado "editando").
     */
    fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        day: LocalDate,
        report: SessionReport,
    )
}
