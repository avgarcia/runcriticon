package com.runcriticon.seguimiento.application.usecases.alerts

import com.runcriticon.seguimiento.application.RecordingAccessAuditor
import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader
import com.runcriticon.seguimiento.application.usecases.report.InMemoryConsentReader
import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * [ListCoachAlertsQuery] con dobles en memoria: el filtro por consentimiento vigente (ADR-0014 D18) vive en
 * el caso de uso, no en [CoachAlertReader], así que se prueba aquí sin Postgres. La auditoría real del acceso
 * (que el aspecto envuelva el bean) la cubre `ListCoachAlertsQueryAuditAccessIntegrationTest`.
 */
class ListCoachAlertsQueryTest :
    FunSpec({
        val clubId = ClubId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ENTRENADOR)
        val group = GroupId.of(UUID.randomUUID())
        val withConsent = StudentId.of(UUID.randomUUID())
        val revoked = StudentId.of(UUID.randomUUID())
        val day = LocalDate.parse("2026-09-17")
        val clock = Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC)

        /** Los cuatro tipos de alerta del MVP para un mismo alumno, incluido "sin reportar". */
        fun allAlertsOf(student: StudentId): List<CoachAlert> =
            listOf(
                CoachAlert.PainReported(
                    studentId = student,
                    groupId = group,
                    day = day,
                    notes = "Pinchazo en el isquio",
                    reportedAt = Instant.parse("2026-09-17T19:00:00Z"),
                ),
                CoachAlert.PaceOffTarget(student, group, day, notes = "Fui mucho más lento"),
                CoachAlert.InjuryDeclared(student, group, day, message = "Me duele la rodilla"),
                CoachAlert.NoReportInDays(student, group, daysSinceLastReport = 9, lastReportedAt = null),
            )

        fun query(
            alerts: List<CoachAlert>,
            consent: InMemoryConsentReader,
        ) = ListCoachAlertsQuery(
            reader = FixedCoachAlertReader(alerts),
            consentReader = consent,
            clock = clock,
            auditor = mockk(relaxed = true),
        )

        test("un alumno que revoco el consentimiento desaparece del panel con todas sus alertas") {
            val result =
                query(allAlertsOf(withConsent) + allAlertsOf(revoked), InMemoryConsentReader(revoked = setOf(revoked)))
                    .execute(coach)
                    .shouldBeRight()

            result.alerts shouldHaveSize 4
            result.alerts.map { it.studentId }.toSet() shouldBe setOf(withConsent)
        }

        test("solo se auditan los alumnos cuyas alertas se han mostrado") {
            val result =
                query(allAlertsOf(withConsent) + allAlertsOf(revoked), InMemoryConsentReader(revoked = setOf(revoked)))
                    .execute(coach)
                    .shouldBeRight()

            result.auditSubjectIds() shouldBe setOf(withConsent.value)
        }

        test("sin consentimiento vigente de nadie (fail-closed) el panel queda vacio") {
            val result =
                query(allAlertsOf(withConsent), InMemoryConsentReader(granted = false))
                    .execute(coach)
                    .shouldBeRight()

            result.alerts.shouldBeEmpty()
        }

        test("un ALUMNO no puede ver el panel de alertas, y el rechazo queda auditado") {
            val alumno = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ALUMNO)
            val audit = RecordingAccessAuditor()
            val consent = InMemoryConsentReader()

            ListCoachAlertsQuery(FixedCoachAlertReader(allAlertsOf(withConsent)), consent, clock, audit.auditor)
                .execute(alumno)
                .shouldBeLeft(SeguimientoError.Forbidden)

            consent.calls.shouldBeEmpty()
            audit.shouldHaveDeniedRbac(alumno, "COACH_ALERT:LIST")
        }

        test("sin alertas no consulta el consentimiento") {
            val consent = InMemoryConsentReader()

            query(emptyList(), consent).execute(coach).shouldBeRight()

            consent.calls.shouldBeEmpty()
        }
    })

private class FixedCoachAlertReader(
    private val alerts: List<CoachAlert>,
) : CoachAlertReader {
    override fun findActiveAlerts(
        clubId: ClubId,
        coachId: CoachId,
        groupId: GroupId?,
        today: LocalDate,
    ): List<CoachAlert> = alerts
}
