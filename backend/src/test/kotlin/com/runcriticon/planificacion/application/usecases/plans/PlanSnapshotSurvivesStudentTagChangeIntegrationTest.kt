package com.runcriticon.planificacion.application.usecases.plans

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.AssignStudentTagCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.UnassignStudentTagCommand
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * Cierra el AC2 de LAL-87: el disparador es un cambio de tags de verdad, no una escritura directa contra
 * `miembro_grupo` como hace `PublishPlanIntegrationTest`. Recorre la cadena completa entre módulos —
 * `UnassignStudentTagCommand` (club_taxonomia) → `MembresiaDeGrupoCambiada` por el outbox real →
 * `GroupMembersProjectionListener` (planificacion) — y comprueba que, aun así, `plan_snapshot_alumno` no se
 * entera. Los tramos intermedios de esa cadena ya están cubiertos en otro sitio (`GroupMembershipEventPublication-
 * IntegrationTest` para el primer salto, `GroupMembersProjectionEventFlowIntegrationTest` para el segundo); aquí
 * solo se prueba el tramo que ninguno de los dos cubre.
 */
class PlanSnapshotSurvivesStudentTagChangeIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var publishPlan: PublishPlanCommand

    @Autowired private lateinit var plans: WeeklyPlanRepository

    @Autowired private lateinit var assignTag: AssignStudentTagCommand

    @Autowired private lateinit var unassignTag: UnassignStudentTagCommand

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val group = UuidCreator.getTimeOrderedEpoch()
    private val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
    private val monday = LocalDate.of(2026, 8, 17)

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
        // Mismo motivo que `PublishPlanIntegrationTest.limpiaElContexto`: `event_publication` es una tabla
        // compartida por toda la JVM de tests. No tiene columna `aggregate_id` -- el id del grupo va dentro de
        // `serialized_event` (TEXT) -- así que el filtro es por ese payload en vez de por columna.
        jdbc.update(
            """
            DELETE FROM event_publication
            WHERE event_type LIKE '%MembresiaDeGrupoCambiada' AND serialized_event LIKE ?
            """.trimIndent(),
            "%$group%",
        )
    }

    @Test
    fun `quitar el tag que sostenia la pertenencia de un alumno no altera el snapshot de un plan ya publicado`() {
        val alumno = UuidCreator.getTimeOrderedEpoch()
        val valor = sembrarValorDeTag("nivel", "medio")
        sembrarGrupoConFiltro(valor)
        sembrarAlumno(alumno)
        sembrarEntrenador()

        // Asignar el tag por el comando real (no una fila cruda en miembro_grupo): así la pertenencia inicial
        // llega por el mismo camino -- MembresiaDeGrupoCambiada + outbox -- que la baja que se prueba después.
        autenticar(admin)
        enTransaccion { assignTag.execute(admin, alumno, valor) }.shouldBeRight()

        // La proyección tiene que reflejar la pertenencia inicial antes de publicar -- si el snapshot se
        // congela vacío, el resto del test no demuestra nada.
        awaitMiembro(alumno)

        val plan = crearPlanConSesion()
        enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeRight()
        snapshotDe(plan.id) shouldContainExactlyInAnyOrder listOf(alumno)

        autenticar(admin)
        enTransaccion { unassignTag.execute(admin, alumno, valor) }.shouldBeRight()

        // El alumno debe desaparecer de la proyección en vivo -- sin este await, un evento que nunca llega
        // también dejaría el snapshot intacto, y el test pasaría sin haber probado nada.
        awaitAusente(alumno)
        snapshotDe(plan.id) shouldContainExactlyInAnyOrder listOf(alumno)
    }

    private fun crearPlanConSesion(): WeeklyPlan {
        autenticar(coach)
        val draft =
            WeeklyPlan.createDraft(club, GroupId.of(group), PersonId.of(coach.userId), monday).shouldBeRight()
        enTransaccion { plans.save(club, draft) }
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        enTransaccion { plans.insertSession(club, draft.id, session) }
        return enTransaccion { plans.findById(club, draft.id) }!!
    }

    private fun sembrarValorDeTag(
        eje: String,
        valor: String,
    ): UUID {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            "$eje-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            valueId,
            keyId,
            club.value,
            valor,
        )
        return valueId
    }

    private fun sembrarGrupoConFiltro(valor: UUID) {
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)",
            group,
            club.value,
            "Grupo",
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
            group,
            club.value,
            valor,
        )
    }

    private fun sembrarAlumno(personId: UUID) {
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, 'Alumna de prueba', ?, 'ALUMNO', 'ACTIVO', ?, now())
            """.trimIndent(),
            personId,
            club.value,
            "alumna-$personId@club.test",
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    private fun sembrarEntrenador() {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ENTRENADOR', ?, now())
            """.trimIndent(),
            group,
            club.value,
            coach.userId,
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    private fun snapshotDe(planId: PlanId): List<UUID> =
        jdbc
            .queryForList(
                "SELECT alumno_id FROM planificacion.plan_snapshot_alumno WHERE plan_id = ?",
                UUID::class.java,
                planId.value,
            ).filterNotNull()

    private fun awaitMiembro(alumno: UUID) =
        await("el alumno $alumno no se proyectó en miembro_grupo") { if (leerMiembro(alumno)) Unit else null }

    private fun awaitAusente(alumno: UUID) =
        await("el alumno $alumno no se borró de miembro_grupo") { if (!leerMiembro(alumno)) Unit else null }

    private fun leerMiembro(alumno: UUID): Boolean =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.miembro_grupo WHERE grupo_id = ? AND persona_id = ? AND rol = 'ALUMNO'",
            Int::class.java,
            group,
            alumno,
        )!! > 0

    private fun <T> await(
        failure: String,
        probe: () -> T?,
    ): T {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            probe()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("$failure en $DEADLINE_SECONDS s")
    }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun autenticar(principal: Principal) {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SUFIJO_UNICO = 8
    }
}
