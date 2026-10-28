package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.planificacion.infrastructure.observability.PlanificacionMetrics
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

/**
 * `ProjectionFreshness.membersProjectionLagSeconds()` y el gauge `planificacion.projection_lag_seconds` contra el
 * outbox real. `event_publication` la comparte toda la JVM de tests, así que los asertos son cotas (una entrega
 * sembrada con horas de antigüedad domina sobre cualquier entrega en vuelo de otro test), nunca igualdades, y solo
 * se borra lo que este test siembra. Cada fila lleva un grupo aleatorio en `aggregateId` para no tocar la puerta de
 * publicación, que está acotada al grupo.
 */
class MembersProjectionLagIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var freshness: ProjectionFreshness

    @Autowired private lateinit var metrics: PlanificacionMetrics

    @Autowired private lateinit var meterRegistry: MeterRegistry

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiaLoSembrado() {
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%\"$MARCADOR_TEST\"%")
    }

    @Test
    fun `una entrega pendiente a la proyeccion alimenta el lag y el gauge con su antiguedad`() {
        seedDelivery(PROJECTION_LISTENER, MembresiaDeGrupoCambiada::class, age = HOURS_OLD)

        freshness.membersProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
        metrics.membersProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds.toDouble()
    }

    /** Los handlers de entrenadores también escriben en `miembro_grupo`: su retraso es retraso de la proyección. */
    @Test
    fun `una entrega pendiente de un evento de entrenadores tambien cuenta`() {
        seedDelivery(PROJECTION_LISTENER, EntrenadorAsignadoAGrupo::class, age = HOURS_OLD)

        freshness.membersProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
    }

    /** Una `FAILED` no converge sola: tiene que seguir haciendo crecer el gauge hasta que se reprocese. */
    @Test
    fun `una entrega FAILED sigue contando`() {
        seedDelivery(PROJECTION_LISTENER, MembresiaDeGrupoCambiada::class, age = HOURS_OLD, status = "FAILED")

        freshness.membersProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
    }

    /**
     * `MergeSuggestionListener` de `club_taxonomia` escucha el mismo evento con su propia fila en el outbox: su retraso
     * no es el de esta proyección. Una entrega ya completada tampoco es retraso.
     */
    @Test
    fun `las entregas de otro listener y las completadas no cuentan`() {
        seedDelivery(FOREIGN_LISTENER, MembresiaDeGrupoCambiada::class, age = DAY_OLD)
        seedDelivery(PROJECTION_LISTENER, MembresiaDeGrupoCambiada::class, age = DAY_OLD, completed = true)

        freshness.membersProjectionLagSeconds() shouldBeLessThan DAY_OLD.seconds
    }

    /**
     * Contra el registro de Micrometer, no contra el bean: lo que consumen el scrape y la alarma es el **nombre** de
     * la métrica con sus tags, y un aserto sobre el bean pasaría igual con el nombre mal escrito o sin el tag
     * `projection`.
     */
    @Test
    fun `el gauge esta registrado con su nombre y sus tags`() {
        val gauge =
            meterRegistry
                .find("planificacion.projection_lag_seconds")
                .tag("module", "planificacion")
                .tag("projection", "miembro_grupo")
                .gauge()

        gauge.shouldNotBeNull()
    }

    private fun seedDelivery(
        listener: String,
        event: KClass<*>,
        age: Duration,
        status: String = "PUBLISHED",
        completed: Boolean = false,
    ) {
        val publishedAt = Instant.now().minus(age)
        jdbc.update(
            """
            INSERT INTO event_publication
                (id, listener_id, event_type, serialized_event, publication_date, completion_date, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            "$listener.on(${event.java.name})",
            event.java.name,
            """{"$MARCADOR_TEST":true,"aggregateId":"${UUID.randomUUID()}"}""",
            Timestamp.from(publishedAt),
            if (completed) Timestamp.from(publishedAt.plusSeconds(1)) else null,
            if (completed) "COMPLETED" else status,
        )
    }

    private companion object {
        val PROJECTION_LISTENER: String = GroupMembersProjectionListener::class.java.name

        // Literal y no `::class`: el test es de `planificacion` y no debe depender de un listener de otro módulo.
        const val FOREIGN_LISTENER = "com.runcriticon.clubtaxonomia.application.listeners.MergeSuggestionListener"

        /** Muy por encima del umbral de 60 s y de cualquier entrega en vuelo, para no depender del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        val DAY_OLD: Duration = Duration.ofDays(1)

        /** Marca las filas que siembra este test, para borrarlas sin tocar las de otros. */
        const val MARCADOR_TEST = "sembradaPorMembersProjectionLagIntegrationTest"
    }
}
