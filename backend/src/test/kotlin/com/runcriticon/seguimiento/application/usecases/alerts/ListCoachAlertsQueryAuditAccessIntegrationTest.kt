package com.runcriticon.seguimiento.application.usecases.alerts

import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.api.events.AccesoADatosSensibles
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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
 * Contra Postgres real (LAL-121): que [ListCoachAlertsQuery] esté anotado con `@AuditAccess` no basta —
 * `AuditAccessAspect` solo se dispara si el proxy AOP de Spring envuelve el bean de verdad. Ningún test
 * existente lo comprobaba (`CoachAlertReaderJdbcIntegrationTest` autowirea el repositorio JDBC, no el caso
 * de uso, así que nunca pasa por el aspecto); `ListCoachAlertsQueryTest` usa dobles en memoria y tampoco
 * puede verlo.
 *
 * Este test **encontró un bug real** al escribirse: `execute` llevaba `@Transactional(readOnly = true)` —
 * razonable a primera vista, el caso de uso solo lee — pero eso impedía que el `AccesoADatosSensibles` del
 * aspecto llegara nunca al outbox (readOnly se propaga a la conexión JDBC, que en PostgreSQL rechaza la
 * escritura sin lanzar una excepción visible). El aspecto se disparaba, calculaba los sujetos correctos,
 * llamaba a `publishEvent`, y aun así `event_publication` se quedaba sin fila: cero señales de que algo
 * fallaba. Ver el KDoc de `AuditAccessAspect` para el detalle; `RgpdArchTest` ahora lo verifica de forma
 * mecánica para que no vuelva a colarse en un `@AuditAccess` futuro.
 */
class ListCoachAlertsQueryAuditAccessIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var query: ListCoachAlertsQuery

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val today: LocalDate = LocalDate.parse("2026-09-18")
    private val createdStudentIds = mutableListOf<UUID>()

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
        // `event_publication` es compartida por toda la JVM de tests (mismo motivo que
        // `PublishPlanIntegrationTest.limpiaElContexto`): filtra por el id del alumno de este test, no borra
        // en bloque por `event_type`.
        createdStudentIds.forEach { studentId ->
            jdbc.update(
                "DELETE FROM event_publication WHERE event_type LIKE '%AccesoADatosSensibles' " +
                    "AND serialized_event LIKE ?",
                "%$studentId%",
            )
        }
    }

    @Test
    fun `una alerta activa publica un AccesoADatosSensibles con el alumno como sujeto`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        createdStudentIds += studentId.value
        autenticar(clubId, coachId)
        seedCoachGroup(clubId, groupId, coachId)
        val (planId, day) = seedResolvedSession(clubId, groupId, studentId, dia = today.minusDays(1))
        seedReport(clubId, studentId, planId, day, marcaDolor = true, notas = "Pinchazo en el isquio")

        val result = query.execute(Principal(userId = coachId.value, clubId = clubId.value, role = Role.ENTRENADOR))
        result.shouldBeRight()

        val eventos =
            jdbc.queryForList(
                "SELECT event_type FROM event_publication WHERE event_type LIKE '%AccesoADatosSensibles' " +
                    "AND serialized_event LIKE ?",
                String::class.java,
                "%${studentId.value}%",
            )
        eventos shouldBe listOf(AccesoADatosSensibles::class.java.name)
    }

    @Test
    fun `sin alertas activas no publica ningun AccesoADatosSensibles`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val coachId = CoachId.of(UUID.randomUUID())
        autenticar(clubId, coachId)

        val result = query.execute(Principal(userId = coachId.value, clubId = clubId.value, role = Role.ENTRENADOR))

        result.shouldBeRight().alerts.shouldBeEmpty()
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
        groupId: GroupId,
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
            groupId.value,
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
            java.sql.Timestamp.from(Instant.now()),
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
