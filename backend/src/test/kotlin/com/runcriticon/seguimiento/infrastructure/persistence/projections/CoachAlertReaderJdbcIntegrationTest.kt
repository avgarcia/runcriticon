package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Round-trip de [CoachAlertReaderJdbc] contra Postgres real (LAL-116): las dos consultas (`reporte_sesion` y
 * "sin reportar"), el filtro "solo mis grupos" vía `grupo_entrenador`, y el caso borde `grupo_id IS NULL`
 * (filas de `plan_resuelto_por_alumno` proyectadas antes de la migración que añadió la columna).
 *
 * Las filas se siembran con SQL directo, no vía los listeners: aísla la lectura de la escritura, mismo
 * criterio que `ResolvedPlanReaderIntegrationTest`.
 */
class CoachAlertReaderJdbcIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var reader: CoachAlertReaderJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val today: LocalDate = LocalDate.parse("2026-09-04")

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `un reporte con marca de dolor genera una alerta de dolor reportado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        val (planId, day) = seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(1))
        seedReport(clubId, studentId, planId, day, marcaDolor = true, notas = "Pinchazo en el isquio")

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        val alert = alerts.filterIsInstance<CoachAlert.PainReported>().single()
        alert.studentId shouldBe studentId
        alert.groupId shouldBe groupId
        alert.notes shouldBe "Pinchazo en el isquio"
    }

    @Test
    fun `una nota con lenguaje de ritmo fuera de objetivo genera esa alerta`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        val (planId, day) = seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(2))
        seedReport(clubId, studentId, planId, day, notas = "Fui a tope, iba por encima del ritmo previsto")

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        val alert = alerts.filterIsInstance<CoachAlert.PaceOffTarget>().single()
        alert.studentId shouldBe studentId
        alert.notes shouldBe "Fui a tope, iba por encima del ritmo previsto"
    }

    @Test
    fun `una nota sin lenguaje de desviacion no genera alerta de ritmo`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        val (planId, day) = seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(1))
        seedReport(clubId, studentId, planId, day, notas = "Buena sesion, comodo")

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        alerts.shouldBeEmpty()
    }

    @Test
    fun `un alumno sin reportar en mas de 7 dias genera alerta de sin reportar`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        // Plan resuelto sin ningun reporte asociado: primera_sesion queda a mas de 7 dias de "today".
        seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(10))

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        val alert = alerts.single().shouldBeInstanceOf<CoachAlert.NoReportInDays>()
        alert.studentId shouldBe studentId
        alert.lastReportedAt shouldBe null
        alert.daysSinceLastReport shouldBe 10L
    }

    @Test
    fun `un alumno que reporto hace 3 dias no genera alerta de sin reportar`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        val (planId, day) = seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(3))
        val reportadoEn = today.minusDays(3).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
        seedReport(clubId, studentId, planId, day, reportadoEn = reportadoEn)

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        alerts.shouldBeEmpty()
    }

    @Test
    fun `una fila con grupo_id NULL (legacy, pre-migracion) no genera ninguna alerta`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        seedResolvedSession(clubId, groupId = null, studentId, dia = today.minusDays(10))

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        alerts.shouldBeEmpty()
    }

    @Test
    fun `un grupo ajeno al entrenador no aparece aunque tenga alertas`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val ownGroup = GroupId.of(UUID.randomUUID())
        val otherGroup = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, ownGroup, coachId)
        // El entrenador NO lleva otherGroup.
        seedResolvedSession(clubId, otherGroup, studentId, dia = today.minusDays(10))

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = null, today = today)

        alerts.shouldBeEmpty()
    }

    @Test
    fun `filtrar por grupoId acota a un solo grupo del entrenador`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupA = GroupId.of(UUID.randomUUID())
        val groupB = GroupId.of(UUID.randomUUID())
        val studentA = StudentId.of(UUID.randomUUID())
        val studentB = StudentId.of(UUID.randomUUID())
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupA, coachId)
        seedCoachGroup(clubId, groupB, coachId)
        seedResolvedSession(clubId, groupA, studentA, dia = today.minusDays(10))
        seedResolvedSession(clubId, groupB, studentB, dia = today.minusDays(10))

        val alerts = reader.findActiveAlerts(clubId, coachId, groupId = groupA, today = today)

        alerts shouldHaveSize 1
        (alerts.single() as CoachAlert.NoReportInDays).studentId shouldBe studentA
    }

    private fun seedCoachGroup(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.grupo_entrenador
                (grupo_id, club_id, entrenador_id, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            groupId.value,
            clubId.value,
            coachId.value,
            UUID.randomUUID(),
            java.sql.Timestamp.from(Instant.now()),
        )
    }

    /** @return `(planId, dia)` de la fila insertada, para anclar el reporte a la misma clave natural. */
    private fun seedResolvedSession(
        clubId: ClubId,
        groupId: GroupId?,
        studentId: StudentId,
        dia: LocalDate,
    ): Pair<UUID, LocalDate> {
        val planId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, grupo_id, dia, sesion_resuelta, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            studentId.value,
            planId,
            clubId.value,
            groupId?.value,
            dia,
            """{"tipo":"RODAJE"}""",
            UUID.randomUUID(),
            java.sql.Timestamp.from(Instant.now()),
        )
        return planId to dia
    }

    private fun seedReport(
        clubId: ClubId,
        studentId: StudentId,
        planId: UUID,
        dia: LocalDate,
        marcaDolor: Boolean = false,
        notas: String? = null,
        reportadoEn: Instant = Instant.now(),
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.reporte_sesion
                (alumno_id, plan_id, dia, club_id, estado, valoracion, notas, marca_dolor, reportado_en)
            VALUES (?, ?, ?, ?, 'HECHO', 3, ?, ?, ?)
            """.trimIndent(),
            studentId.value,
            planId,
            dia,
            clubId.value,
            notas,
            marcaDolor,
            java.sql.Timestamp.from(reportadoEn),
        )
    }

    private fun autenticar(
        clubId: ClubId,
        coachId: CoachId,
    ) {
        val principal = Principal(userId = coachId.value, clubId = clubId.value, role = Role.ENTRENADOR)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }
}
