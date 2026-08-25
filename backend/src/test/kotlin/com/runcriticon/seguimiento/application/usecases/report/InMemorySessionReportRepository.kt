package com.runcriticon.seguimiento.application.usecases.report

import com.runcriticon.seguimiento.application.ports.outbound.persistence.SessionReportRepository
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.LocalDate

/** Registro de una llamada a [SessionReportRepository.upsert]. */
data class SessionReportUpsertCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val planId: PlanId,
    val day: LocalDate,
    val report: SessionReport,
)

/** Doble en memoria del puerto de escritura, registrando con qué se le llamó. */
class InMemorySessionReportRepository : SessionReportRepository {
    val calls: MutableList<SessionReportUpsertCall> = mutableListOf()

    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        day: LocalDate,
        report: SessionReport,
    ) {
        calls += SessionReportUpsertCall(clubId, studentId, planId, day, report)
    }
}
