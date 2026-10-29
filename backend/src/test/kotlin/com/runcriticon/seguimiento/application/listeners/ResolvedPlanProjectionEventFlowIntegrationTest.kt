package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.PublishedPersonalization
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Flujo completo de extremo a extremo: publicar `PlanPublicado` en la transacción de un caso de uso,
 * dejar que el outbox lo entregue tras el commit, y comprobar el estado final de `plan_resuelto_por_alumno`.
 * Mismo patrón que `PersonProjectionEventFlowIntegrationTest`/`GroupMembersProjectionEventFlowIntegrationTest`.
 */
class ResolvedPlanProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `publicar un plan proyecta una fila por alumno y por sesion`() {
        val planId = UUID.randomUUID()
        val alumno1 = UUID.randomUUID()
        val alumno2 = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno1, alumno2),
                sessions =
                    listOf(
                        session("2026-08-17", "RODAJE"),
                        session("2026-08-19", "TEMPO", ritmoTipo = "ABSOLUTO", ritmoSegundosPorKm = 240),
                    ),
            ),
        )

        awaitRowCount(planId, expected = 4)
    }

    @Test
    fun `una sesion con ritmo absoluto persiste el segundos por km resuelto`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-19", "TEMPO", ritmoTipo = "ABSOLUTO", ritmoSegundosPorKm = 240)),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19"))
        row["ritmo_tipo_origen"] shouldBe "ABSOLUTO"
        row["ritmo_calculado_seg_por_km"] shouldBe 240
        row["ritmo_referencia_distancia"] shouldBe null
    }

    @Test
    fun `una sesion con ritmo relativo y el alumno sin esa marca persiste falta de marca, no un segundos por km`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno),
                sessions =
                    listOf(
                        session(
                            "2026-08-19",
                            "TEMPO",
                            ritmoTipo = "RELATIVO",
                            ritmoReferencia = "10K",
                            ritmoDeltaSegundosPorKm = 10,
                        ),
                    ),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19"))
        row["ritmo_calculado_seg_por_km"] shouldBe null
        row["ritmo_falta_marca"] shouldBe "10K"
        row["ritmo_delta_seg_por_km"] shouldBe 10
    }

    @Test
    fun `una sesion con ritmo relativo y el alumno con marca previa resuelve marca + delta al publicar`() {
        val planId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400) // 240 s/km

        publish(
            planPublicado(
                planId = planId,
                clubId = clubId,
                students = listOf(alumno),
                sessions =
                    listOf(
                        session(
                            "2026-08-19",
                            "TEMPO",
                            ritmoTipo = "RELATIVO",
                            ritmoReferencia = "10K",
                            ritmoDeltaSegundosPorKm = 10,
                        ),
                    ),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19"))
        row["ritmo_tipo_origen"] shouldBe "RELATIVO"
        row["ritmo_calculado_seg_por_km"] shouldBe 250
        row["ritmo_referencia_distancia"] shouldBe "10K"
        row["ritmo_falta_marca"] shouldBe null
        row["ritmo_delta_seg_por_km"] shouldBe 10
    }

    @Test
    fun `sin personalizaciones en el evento las columnas de personalizacion quedan vacias`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(planPublicado(planId = planId, students = listOf(alumno)))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-17"))
        row["mensaje_al_alumno"] shouldBe null
        row["es_personalizada"] shouldBe false
    }

    @Test
    fun `una personalizacion creada sobre el borrador llega al alumno al publicar`() {
        // SetPersonalizationCommand solo emite PersonalizacionAplicada si el plan ya estaba PUBLICADO: la
        // personalización hecha sobre el BORRADOR viaja embebida en PlanPublicado.personalizaciones. Si el
        // listener la ignora, el alumno ve la sesión base para siempre y ninguna reentrega del outbox lo repara.
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-17", "RODAJE")),
                personalizaciones =
                    listOf(
                        PublishedPersonalization(
                            sesionId = UUID.randomUUID(),
                            dia = LocalDate.parse("2026-08-17"),
                            alumnoId = alumno,
                            override =
                                PersonalizedSession(
                                    tipo = "TEMPO",
                                    volumenTipo = null,
                                    volumenMetros = null,
                                    volumenMinutos = null,
                                    ritmoTipo = "ABSOLUTO",
                                    ritmoSegundosPorKm = 240,
                                    ritmoReferencia = null,
                                    ritmoDeltaSegundosPorKm = null,
                                    notas = null,
                                ),
                            mensajeAlAlumno = "Hoy ritmo suave, te cuidamos la rodilla",
                        ),
                    ),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-17"))
        row["es_personalizada"] shouldBe true
        row["mensaje_al_alumno"] shouldBe "Hoy ritmo suave, te cuidamos la rodilla"
        // La sesión base ("RODAJE", sin ritmo) no tiene ritmo_tipo_origen: que aquí sea ABSOLUTO con 240
        // s/km es la prueba de que se aplicó el override, no la sesión base.
        row["ritmo_tipo_origen"] shouldBe "ABSOLUTO"
        row["ritmo_calculado_seg_por_km"] shouldBe 240
    }

    @Test
    fun `reentregar el mismo evento no duplica filas`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val event = planPublicado(planId = planId, students = listOf(alumno))

        publish(event)
        awaitRowCount(planId, expected = 1)
        publish(event)

        OutboxDeliveries(jdbc).awaitCompleted(ResolvedPlanProjectionListener::class, event.eventId, times = 2)
        countRows(planId) shouldBe 1
        countProcessed(event.eventId) shouldBe 1
    }

    @Test
    fun `cada fila proyectada recoge el evento de publicacion que la escribio`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val event = planPublicado(planId = planId, students = listOf(alumno))

        publish(event)
        awaitRowCount(planId, expected = 1)

        val row = readRow(planId, alumno, LocalDate.parse("2026-08-17")).shouldNotBeNull()
        row["last_processed_event_id"] shouldBe event.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe event.occurredAt
    }

    @Test
    fun `dos planes de grupos distintos pueden resolver el mismo dia para el mismo alumno`() {
        val alumno = UUID.randomUUID()
        val planA = UUID.randomUUID()
        val planB = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planA,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-17", "RODAJE")),
            ),
        )
        awaitRowCount(planA, expected = 1)
        publish(
            planPublicado(
                planId = planB,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-17", "TEMPO")),
            ),
        )
        awaitRowCount(planB, expected = 1)

        // Sin UNIQUE (alumno_id, dia): las dos filas conviven, cada una con su plan_id — el desempate de cuál se
        // muestra es responsabilidad del lector (ResolvedPlanReaderIntegrationTest), no de este listener.
        countRowsForStudentAndDay(alumno, LocalDate.parse("2026-08-17")) shouldBe 2
    }

    /** Publica dentro de una transacción: `@ApplicationModuleListener` solo entrega tras un commit. */
    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRowCount(
        planId: UUID,
        expected: Int,
    ) {
        await("no se proyectaron las $expected filas del plan $planId") {
            countRows(planId).takeIf { it == expected }
        }
    }

    private fun awaitRow(
        planId: UUID,
        alumnoId: UUID,
        dia: LocalDate,
    ): Map<String, Any?> = await("no se proyectó la fila de $alumnoId/$dia") { readRow(planId, alumnoId, dia) }

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

    private fun readRow(
        planId: UUID,
        alumnoId: UUID,
        dia: LocalDate,
    ): Map<String, Any?>? =
        jdbc
            .queryForList(
                "SELECT * FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ? AND alumno_id = ? AND dia = ?",
                planId,
                alumnoId,
                dia,
            ).firstOrNull()

    private fun countRows(planId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ?",
            Int::class.java,
            planId,
        ) ?: 0

    private fun countRowsForStudentAndDay(
        alumnoId: UUID,
        dia: LocalDate,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ? AND dia = ?",
            Int::class.java,
            alumnoId,
            dia,
        ) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private fun insertMark(
        clubId: UUID,
        alumnoId: UUID,
        distancia: String,
        tiempoSegundos: Int,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.marca_alumno (alumno_id, distancia, tiempo_segundos, club_id, modificado_en)
            VALUES (?, ?, ?, ?, now())
            """.trimIndent(),
            alumnoId,
            distancia,
            tiempoSegundos,
            clubId,
        )
    }

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
    }
}

private fun session(
    dia: String,
    tipo: String,
    ritmoTipo: String? = null,
    ritmoSegundosPorKm: Int? = null,
    ritmoReferencia: String? = null,
    ritmoDeltaSegundosPorKm: Int? = null,
) = PublishedSession(
    dia = LocalDate.parse(dia),
    tipo = tipo,
    volumenTipo = null,
    volumenMetros = null,
    volumenMinutos = null,
    ritmoTipo = ritmoTipo,
    ritmoSegundosPorKm = ritmoSegundosPorKm,
    ritmoReferencia = ritmoReferencia,
    ritmoDeltaSegundosPorKm = ritmoDeltaSegundosPorKm,
    notas = null,
)

private fun planPublicado(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    students: List<UUID> = listOf(UUID.randomUUID()),
    sessions: List<PublishedSession> = listOf(session("2026-08-17", "RODAJE")),
    personalizaciones: List<PublishedPersonalization> = emptyList(),
) = PlanPublicado(
    eventId = UUID.randomUUID(),
    aggregateId = planId,
    occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    grupoId = UUID.randomUUID(),
    snapshotAlumnos = students,
    sesiones = sessions,
    personalizaciones = personalizaciones,
)
