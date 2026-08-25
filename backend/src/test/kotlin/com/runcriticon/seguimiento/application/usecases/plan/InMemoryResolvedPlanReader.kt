package com.runcriticon.seguimiento.application.usecases.plan

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.LocalDate

/** Registro de una llamada a [ResolvedPlanReader.findWeek], para comprobar con qué se llamó. */
data class ResolvedPlanReaderCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val from: LocalDate,
    val to: LocalDate,
)

/** Registro de una llamada a [ResolvedPlanReader.findDay] (LAL-30). */
data class ResolvedPlanDayCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val day: LocalDate,
)

/**
 * Doble en memoria del puerto. Además de devolver lo configurado, registra con qué se le llamó — es lo que hay
 * que comprobar en el caso de uso, no el SQL de resolución (ya cubierto contra Postgres real).
 */
class InMemoryResolvedPlanReader(
    private val sessions: List<ResolvedSession> = emptyList(),
) : ResolvedPlanReader {
    val calls: MutableList<ResolvedPlanReaderCall> = mutableListOf()
    val dayCalls: MutableList<ResolvedPlanDayCall> = mutableListOf()

    override fun findWeek(
        clubId: ClubId,
        studentId: StudentId,
        from: LocalDate,
        to: LocalDate,
    ): List<ResolvedSession> {
        calls += ResolvedPlanReaderCall(clubId, studentId, from, to)
        return sessions
    }

    override fun findDay(
        clubId: ClubId,
        studentId: StudentId,
        day: LocalDate,
    ): ResolvedSession? {
        dayCalls += ResolvedPlanDayCall(clubId, studentId, day)
        return sessions.find { it.day == day }
    }
}
