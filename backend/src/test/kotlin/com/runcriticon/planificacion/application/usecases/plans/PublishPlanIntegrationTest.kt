package com.runcriticon.planificacion.application.usecases.plans

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.application.usecases.personalizations.SetPersonalizationCommand
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.Pace
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

    @Autowired private lateinit var setPersonalization: SetPersonalizationCommand

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
        // procesarla al apagar el contexto). [MARCADOR_TEST] en el payload señala las que sembró este test.
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%\"$MARCADOR_TEST\"%")
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

    /**
     * Personalizar sobre el `BORRADOR` no emite evento propio: la personalización solo llega a Seguimiento
     * embebida en `PlanPublicado`. Probar cada lado por separado (el productor rellena el campo, el consumidor
     * lee un evento sintético) no detecta que el otro lo ignore — por eso este test recorre el flujo entero,
     * del caso de uso de Planificación a la proyección de Seguimiento, a través del outbox real.
     */
    @Test
    fun `una personalizacion hecha sobre el borrador llega a la proyeccion del alumno al publicar`() {
        val alumno = UUID.randomUUID()
        sembrarAlumno(alumno)
        val plan = crearPlanConSesion()
        val session = plan.sessions.single()

        enTransaccion {
            setPersonalization.execute(
                actor = coach,
                planId = plan.id,
                sessionId = session.id,
                studentId = PersonId.of(alumno),
                type = SessionType.TEMPO,
                volume = null,
                pace = Pace.Absoluto(secondsPerKm = 240),
                notes = null,
                messageToStudent = "Hoy suave, te cuidamos la rodilla",
            )
        }.shouldBeRight()
        enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeRight()

        val fila = awaitFilaProyectada(plan.id, alumno, session.day)
        fila["es_personalizada"] shouldBe true
        fila["mensaje_al_alumno"] shouldBe "Hoy suave, te cuidamos la rodilla"
        // La sesión base es un RODAJE sin ritmo: un 240 s/km solo puede venir del override.
        fila["ritmo_calculado_seg_por_km"] shouldBe 240
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
        // ProjectionFreshnessJdbc acota por grupo leyendo esta clave del evento serializado.
        fila["grupo"] shouldBe group.value.toString()
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
                    "SELECT event_type, listener_id, serialized_event::jsonb ->> 'aggregateId' AS grupo " +
                        "FROM event_publication " +
                        "WHERE serialized_event LIKE ? AND listener_id LIKE '%GroupMembersProjectionListener%' " +
                        "AND completion_date IS NOT NULL",
                    "%$eventId%",
                )
            filas.firstOrNull()?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("la entrega de $eventId a GroupMembersProjectionListener no se completó en 5s")
    }

    /** `ResolvedPlanProjectionListener` corre asíncrono tras el commit de la publicación: hay que esperar a la fila. */
    private fun awaitFilaProyectada(
        planId: PlanId,
        alumno: UUID,
        dia: LocalDate,
    ): Map<String, Any?> {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            val filas =
                jdbc.queryForList(
                    "SELECT es_personalizada, mensaje_al_alumno, ritmo_calculado_seg_por_km " +
                        "FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ? AND alumno_id = ? AND dia = ?",
                    planId.value,
                    alumno,
                    dia,
                )
            filas.firstOrNull()?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("la proyección de Seguimiento para $alumno el $dia no apareció en 5s")
    }

    @Test
    fun `una publicacion de MembresiaDeGrupoCambiada pendiente desde hace mas de 60s rechaza con ProjectionStale`() {
        val plan = crearPlanConSesion()
        sembrarPublicacionPendiente(Instant.now().minusSeconds(90))

        val error = enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeLeft()

        error.shouldBeInstanceOf<PlanificacionError.ProjectionStale>().lagSeconds shouldBeGreaterThanOrEqual 60L
        snapshotDe(plan.id).shouldBeEmpty()
    }

    /** ADR-0009 D9 deniega solo lo que depende de la relación atrasada: la membresía de otro grupo no cuenta. */
    @Test
    fun `una publicacion pendiente de otro grupo no impide publicar el plan de este`() {
        val plan = crearPlanConSesion()
        sembrarPublicacionPendiente(Instant.now().minusSeconds(3_600), grupo = GroupId.of(UUID.randomUUID()))

        enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeRight()
    }

    @Test
    fun `una publicacion FAILED del grupo rechaza indicando que no se resolvera sola`() {
        val plan = crearPlanConSesion()
        sembrarPublicacionPendiente(Instant.now().minusSeconds(400), status = "FAILED")

        val error = enTransaccion { publishPlan.execute(coach, plan.id) }.shouldBeLeft()

        error.shouldBeInstanceOf<PlanificacionError.ProjectionStale>().failed shouldBe true
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

    /**
     * Fila cruda en `event_publication` sin completar, como la dejaría un listener aún no ejecutado. El payload lleva
     * el grupo en `aggregateId`, como la serialización real, y [MARCADOR_TEST] para que [limpiaElContexto] la borre.
     */
    private fun sembrarPublicacionPendiente(
        publicationDate: Instant,
        grupo: GroupId = group,
        status: String = "PUBLISHED",
    ) {
        jdbc.update(
            """
            INSERT INTO event_publication
                (id, listener_id, event_type, serialized_event, publication_date, completion_date, status)
            VALUES (?, ?, ?, ?, ?, NULL, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            "${GroupMembersProjectionListener::class.java.name}.on(${MembresiaDeGrupoCambiada::class.java.name})",
            MembresiaDeGrupoCambiada::class.java.name,
            """{"$MARCADOR_TEST":true,"aggregateId":"${grupo.value}"}""",
            Timestamp.from(publicationDate),
            status,
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

/** Marca las filas de `event_publication` que siembra este test, para borrarlas sin tocar las de otros. */
private const val MARCADOR_TEST = "sembradaPorPublishPlanIntegrationTest"
