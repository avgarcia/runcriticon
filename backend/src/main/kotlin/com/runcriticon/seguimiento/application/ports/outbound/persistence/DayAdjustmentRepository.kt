package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/**
 * Escritura de reajustes de día del alumno (LAL-33). Puerto aparte de [ResolvedPlanReader]: el reajuste es su
 * propio agregado con escritura, `ResolvedPlanReader.findWeek`/`findDay` lo leen por `LEFT JOIN` para resolver
 * el día efectivo — incluida la comprobación de si un día destino ya está ocupado, que se hace con
 * `ResolvedPlanReader.findDay`, no con un método de consulta aparte en este puerto.
 */
interface DayAdjustmentRepository {
    /**
     * Crea o reemplaza el reajuste de [studentId] para el plan [planId], anclado a [DayAdjustment.plannedDay]
     * — envío idempotente, igual que [SessionReportRepository.upsert]. [planId] es el plan de la sesión de
     * ESTA fila, nunca uno "de la operación completa": en un intercambio entre sesiones de planes distintos,
     * cada fila lleva su propio [PlanId].
     */
    fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        adjustment: DayAdjustment,
    )

    /**
     * Borra TODAS las filas de la operación [operationId] — nunca una sola fila: un
     * `REEMPLAZAR`/`INTERCAMBIAR` escribió dos, y deshacer solo una dejaría la operación a medias (una sesión
     * movida sin que la otra vuelva a su sitio).
     */
    fun deleteByOperation(
        clubId: ClubId,
        studentId: StudentId,
        operationId: UUID,
    ): Int
}
