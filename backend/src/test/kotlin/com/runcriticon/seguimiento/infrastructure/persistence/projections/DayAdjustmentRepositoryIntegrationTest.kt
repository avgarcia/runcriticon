package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
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
 * [DayAdjustmentRepositoryJdbc] contra Postgres real (LAL-33): el upsert como edición sobre la clave natural
 * `(alumno_id, plan_id, dia)`, el borrado por `operacion_id` (no por fila suelta) y el índice único que
 * impide que dos sesiones distintas reclamen el mismo día destino.
 */
class DayAdjustmentRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var repository: DayAdjustmentRepositoryJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `un segundo envio el mismo dia planificado edita la fila en vez de duplicarla`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val planId = PlanId.of(UUID.randomUUID())
        val dia = LocalDate.parse("2026-09-02")
        autenticar(clubId, studentId)

        repository.upsert(clubId, studentId, planId, reajuste(dia, AdjustmentAction.SALTADA))
        val editado = reajuste(dia, AdjustmentAction.MOVIDA, targetDay = dia.plusDays(1))
        repository.upsert(clubId, studentId, planId, editado)

        val filas = jdbc.queryForObject(COUNT_SQL, Int::class.java, studentId.value, planId.value, dia)
        filas shouldBe 1
        val accion =
            jdbc.queryForObject(
                "SELECT accion FROM seguimiento.reajuste_dia WHERE alumno_id = ? AND plan_id = ? AND dia = ?",
                String::class.java,
                studentId.value,
                planId.value,
                dia,
            )
        accion shouldBe "MOVIDA"
    }

    @Test
    fun `borrar por operacion_id borra las dos filas de un reemplazo, no solo una`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val planA = PlanId.of(UUID.randomUUID())
        val planB = PlanId.of(UUID.randomUUID())
        val diaA = LocalDate.parse("2026-09-02")
        val diaB = LocalDate.parse("2026-09-04")
        val operationId = UUID.randomUUID()
        autenticar(clubId, studentId)

        repository.upsert(
            clubId,
            studentId,
            planA,
            reajuste(diaA, AdjustmentAction.MOVIDA, targetDay = diaB, operationId = operationId),
        )
        repository.upsert(
            clubId,
            studentId,
            planB,
            reajuste(diaB, AdjustmentAction.SALTADA, operationId = operationId),
        )

        val borradas = repository.deleteByOperation(clubId, studentId, operationId)

        borradas shouldBe 2
        val restantes =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reajuste_dia WHERE alumno_id = ?",
                Int::class.java,
                studentId.value,
            )
        restantes shouldBe 0
    }

    @Test
    fun `el indice unico rechaza dos MOVIDA al mismo dia destino, aunque se salte la aplicacion`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val diaDestino = LocalDate.parse("2026-09-05")
        autenticar(clubId, studentId)
        jdbc.update(
            INSERT_SQL,
            studentId.value,
            UUID.randomUUID(),
            LocalDate.parse("2026-09-01"),
            clubId.value,
            UUID.randomUUID(),
            "MOVIDA",
            diaDestino,
            "CANSANCIO",
        )

        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                INSERT_SQL,
                studentId.value,
                UUID.randomUUID(),
                LocalDate.parse("2026-09-02"),
                clubId.value,
                UUID.randomUUID(),
                "MOVIDA",
                diaDestino,
                "CANSANCIO",
            )
        }
    }

    @Test
    fun `el CHECK de coherencia rechaza MOVIDA sin destino, aunque se salte el dominio`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO seguimiento.reajuste_dia (alumno_id, plan_id, dia, club_id, operacion_id, accion, motivo)
                VALUES (?, ?, ?, ?, ?, 'MOVIDA', 'CANSANCIO')
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.parse("2026-09-02"),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }
    }

    private fun reajuste(
        plannedDay: LocalDate,
        action: AdjustmentAction,
        targetDay: LocalDate? = null,
        operationId: UUID = UUID.randomUUID(),
    ): DayAdjustment =
        DayAdjustment(
            operationId = operationId,
            action = action,
            plannedDay = plannedDay,
            targetDay = targetDay,
            reason = AdjustmentReason.CANSANCIO,
            createdAt = Instant.parse("2026-09-02T18:00:00Z"),
        )

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
    "SELECT count(*) FROM seguimiento.reajuste_dia WHERE alumno_id = ? AND plan_id = ? AND dia = ?"

private const val INSERT_SQL =
    """
    INSERT INTO seguimiento.reajuste_dia (alumno_id, plan_id, dia, club_id, operacion_id, accion, dia_destino, motivo)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """
