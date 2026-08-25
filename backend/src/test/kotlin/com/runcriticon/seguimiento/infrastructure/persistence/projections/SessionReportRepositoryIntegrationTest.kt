package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [SessionReportRepositoryJdbc] contra Postgres real: el upsert como edición sobre la clave natural
 * `(alumno_id, plan_id, dia)`, el filtro `club_id` de [com.runcriticon.shared.autorizacion.annotations.AuthScope],
 * y que los CHECK de `V202608240002__crea_reporte_sesion.sql` rechazan las combinaciones que
 * [SessionReport.create] ya impide en dominio — defensa en profundidad, no alcanzable desde el caso de uso.
 */
class SessionReportRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var repository: SessionReportRepositoryJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `un segundo envio el mismo dia edita la fila en vez de duplicarla`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val planId = PlanId.of(UUID.randomUUID())
        val dia = LocalDate.parse("2026-08-17")
        autenticar(clubId, studentId)

        repository.upsert(clubId, studentId, planId, dia, reporte(ReportStatus.HECHO, rating = 3))
        repository.upsert(clubId, studentId, planId, dia, reporte(ReportStatus.PARCIAL, rating = 5))

        val filas = jdbc.queryForObject(COUNT_SQL, Int::class.java, studentId.value, planId.value, dia)
        filas shouldBe 1
        val estado =
            jdbc.queryForObject(
                "SELECT estado FROM seguimiento.reporte_sesion WHERE alumno_id = ? AND plan_id = ? AND dia = ?",
                String::class.java,
                studentId.value,
                planId.value,
                dia,
            )
        estado shouldBe "PARCIAL"
    }

    @Test
    fun `dos planes distintos el mismo dia son dos reportes, sin colision`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val dia = LocalDate.parse("2026-08-17")
        autenticar(clubId, studentId)

        repository.upsert(clubId, studentId, PlanId.of(UUID.randomUUID()), dia, reporte(ReportStatus.HECHO, rating = 4))
        repository.upsert(clubId, studentId, PlanId.of(UUID.randomUUID()), dia, reporte(ReportStatus.HECHO, rating = 2))

        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reporte_sesion WHERE alumno_id = ? AND dia = ?",
                Int::class.java,
                studentId.value,
                dia,
            )
        filas shouldBe 2
    }

    @Test
    fun `motivo MOLESTIAS persiste la marca de dolor activa`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val planId = PlanId.of(UUID.randomUUID())
        val dia = LocalDate.parse("2026-08-17")
        autenticar(clubId, studentId)

        repository.upsert(
            clubId,
            studentId,
            planId,
            dia,
            reporte(ReportStatus.NO_HECHO, reason = NotDoneReason.MOLESTIAS),
        )

        val marcaDolor =
            jdbc.queryForObject(
                "SELECT marca_dolor FROM seguimiento.reporte_sesion WHERE alumno_id = ? AND plan_id = ? AND dia = ?",
                Boolean::class.java,
                studentId.value,
                planId.value,
                dia,
            )
        marcaDolor shouldBe true
    }

    @Test
    fun `el CHECK de coherencia rechaza HECHO sin valoracion, aunque se salte el dominio`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO seguimiento.reporte_sesion (alumno_id, plan_id, dia, club_id, estado)
                VALUES (?, ?, ?, ?, 'HECHO')
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.parse("2026-08-17"),
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `el CHECK de coherencia rechaza NO_HECHO sin motivo, aunque se salte el dominio`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO seguimiento.reporte_sesion (alumno_id, plan_id, dia, club_id, estado)
                VALUES (?, ?, ?, ?, 'NO_HECHO')
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.parse("2026-08-17"),
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `el CHECK de valoracion rechaza fuera del rango 1 al 5`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO seguimiento.reporte_sesion (alumno_id, plan_id, dia, club_id, estado, valoracion)
                VALUES (?, ?, ?, ?, 'HECHO', 6)
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.parse("2026-08-17"),
                UUID.randomUUID(),
            )
        }
    }

    private fun reporte(
        status: ReportStatus,
        rating: Int? = null,
        reason: NotDoneReason? = null,
    ): SessionReport =
        SessionReport
            .create(status, rating, reason, notes = null, reportedAt = Instant.parse("2026-08-17T18:00:00Z"))
            .getOrNull()!!

    private fun autenticar(
        clubId: ClubId,
        studentId: StudentId,
    ) {
        val principal = Principal(userId = studentId.value, clubId = clubId.value, role = Role.ALUMNO)
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

private const val COUNT_SQL =
    "SELECT count(*) FROM seguimiento.reporte_sesion WHERE alumno_id = ? AND plan_id = ? AND dia = ?"
