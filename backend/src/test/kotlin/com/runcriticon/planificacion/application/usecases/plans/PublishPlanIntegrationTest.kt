package com.runcriticon.planificacion.application.usecases.plans

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * `PublishPlanCommand` de extremo a extremo contra Postgres real: es el único nivel que puede verificar la
 * congelación de membresía (ADR-0002 D5) y la puerta fail-closed de ADR-0009 D9 sobre el outbox real —
 * `PublishPlanCommandTest` usa dobles en memoria y no puede probar ninguna de las dos.
 */
class PublishPlanIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var publishPlan: PublishPlanCommand

    @Autowired private lateinit var plans: WeeklyPlanRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var events: ApplicationEventPublisher

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val group = GroupId.of(UuidCreator.getTimeOrderedEpoch())
    private val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
    private val monday = LocalDate.of(2026, 8, 17)

    @BeforeEach
    fun prepara() {
        autenticar(coach)
        sembrarEntrenador()
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
        // `event_publication` es una tabla compartida por toda la JVM de tests (IntegrationTestBase usa un
        // único contenedor Postgres) -- una fila sembrada a mano aquí y no limpiada contaminaría el lag que
        // miden los demás tests de esta clase (y el intento real de GroupMembersProjectionListener de
        // procesarla al apagar el contexto). El payload `'{}'` es el marcador de que la sembró este test.
        jdbc.update("DELETE FROM event_publication WHERE serialized_event = '{}'")
    }

    @Test
    fun `publicar congela el snapshot y un cambio de membresia posterior no lo altera`() {
        val alumnoQueSeQueda = UUID.randomUUID()
        val alumnoQueSeVa = UUID.randomUUID()
        sembrarAlumno(alumnoQueSeQueda)
        sembrarAlumno(alumnoQueSeVa)
        val plan = crearPlanConSesion()

        val result = enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeRight()

        result.plan.status shouldBe PlanStatus.PUBLICADO
        result.studentsInSnapshot shouldBe 2
        snapshotDe(plan.id) shouldContainExactlyInAnyOrder listOf(alumnoQueSeQueda, alumnoQueSeVa)

        // Sacar al alumno del grupo DESPUÉS de publicar (ADR-0002 D5): el snapshot ya congelado no
        // debe cambiar, aunque `miembro_grupo` sí refleje la baja.
        jdbc.update(
            "DELETE FROM planificacion.miembro_grupo WHERE grupo_id = ? AND persona_id = ?",
            group.value,
            alumnoQueSeVa,
        )

        snapshotDe(plan.id) shouldContainExactlyInAnyOrder listOf(alumnoQueSeQueda, alumnoQueSeVa)
    }

    @Test
    fun `publicar un grupo sin alumnos deja el snapshot vacio`() {
        val plan = crearPlanConSesion()

        val result = enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeRight()

        result.studentsInSnapshot shouldBe 0
        snapshotDe(plan.id).shouldBeEmpty()
    }

    /**
     * Verifica contra una entrega real (no la fila sintética de `sembrarPublicacionPendiente`) los strings
     * literales de `event_type` y `listener_id` que Spring Modulith graba para la entrega de
     * `MembresiaDeGrupoCambiada` a [GroupMembersProjectionListener] -- son justo los que
     * [com.runcriticon.planificacion.infrastructure.persistence.projections.ProjectionFreshnessJdbc] usa para
     * filtrar `event_publication`. Si alguno de los dos cambiara de formato, la puerta fail-closed de ADR-0009 D9
     * dejaría de encontrar publicaciones pendientes de esta proyección y fallaría **abierta** en silencio (o,
     * peor, seguiría casando la fila de otro listener del mismo evento) — este test es la única red que lo
     * detectaría.
     */
    @Test
    fun `event_type y listener_id de una entrega real coinciden con lo que usa ProjectionFreshnessJdbc`() {
        val eventId = UUID.randomUUID()
        transactions.executeWithoutResult {
            events.publishEvent(
                MembresiaDeGrupoCambiada(
                    eventId = eventId,
                    aggregateId = group.value,
                    occurredAt = Instant.now(),
                    clubId = club.value,
                    actorId = null,
                    traceparent = null,
                    alumnos = emptyList(),
                ),
            )
        }

        val fila = awaitFilaCompletada(eventId)

        fila["event_type"] shouldBe MembresiaDeGrupoCambiada::class.java.name
        fila["listener_id"] shouldBe
            "${GroupMembersProjectionListener::class.java.name}.on(${MembresiaDeGrupoCambiada::class.java.name})"
    }

    /**
     * `MembresiaDeGrupoCambiada` tiene dos listeners (este y `MergeSuggestionListener`) -- cada uno con su
     * propia fila en `event_publication` para el mismo `eventId`. Filtra también por `listener_id LIKE` para
     * quedarse con la fila de [GroupMembersProjectionListener], que es la única que este test comprueba.
     */
    private fun awaitFilaCompletada(eventId: UUID): Map<String, Any?> {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            val filas =
                jdbc.queryForList(
                    "SELECT event_type, listener_id FROM event_publication " +
                        "WHERE serialized_event LIKE ? AND listener_id LIKE '%GroupMembersProjectionListener%' " +
                        "AND completion_date IS NOT NULL",
                    "%$eventId%",
                )
            filas.firstOrNull()?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("la entrega de $eventId a GroupMembersProjectionListener no se completó en 5s")
    }

    @Test
    fun `una publicacion de MembresiaDeGrupoCambiada pendiente desde hace mas de 60s rechaza con ProjectionStale`() {
        val plan = crearPlanConSesion()
        sembrarPublicacionPendiente(Instant.now().minusSeconds(90))

        val error = enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeLeft()

        error.shouldBeInstanceOf<PlanificacionError.ProjectionStale>().lagSeconds shouldBeGreaterThanOrEqual 60L
        snapshotDe(plan.id).shouldBeEmpty()
    }

    private fun crearPlanConSesion(): WeeklyPlan {
        val plan =
            WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
        enTransaccion { plans.save(club, plan) }
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        enTransaccion { plans.insertSession(club, plan.id, session) }
        return enTransaccion { plans.findById(club, plan.id) }!!
    }

    private fun sembrarEntrenador() {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ENTRENADOR', ?, now())
            """.trimIndent(),
            group.value,
            club.value,
            coach.userId,
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    private fun sembrarAlumno(personId: UUID) {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ALUMNO', ?, now())
            """.trimIndent(),
            group.value,
            club.value,
            personId,
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    /** Fila cruda en `event_publication` sin completar, como la dejaría un listener aún no ejecutado. */
    private fun sembrarPublicacionPendiente(publicationDate: Instant) {
        jdbc.update(
            """
            INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date)
            VALUES (?, ?, ?, '{}', ?, NULL)
            """.trimIndent(),
            UUID.randomUUID(),
            "${GroupMembersProjectionListener::class.java.name}.on(${MembresiaDeGrupoCambiada::class.java.name})",
            MembresiaDeGrupoCambiada::class.java.name,
            Timestamp.from(publicationDate),
        )
    }

    private fun snapshotDe(planId: PlanId): List<UUID> =
        jdbc
            .queryForList(
                "SELECT alumno_id FROM planificacion.plan_snapshot_alumno WHERE plan_id = ?",
                UUID::class.java,
                planId.value,
            ).filterNotNull()

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
}
