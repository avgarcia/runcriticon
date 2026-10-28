package com.runcriticon.planificacion.infrastructure.observability

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.planificacion.application.listeners.PlanificacionDeletionListener
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.PendingDeliverySeeder
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration

/**
 * Qué entregas mueven el gauge `planificacion.projection_lag_seconds`.
 *
 * La lista de listeners que escriben en `miembro_grupo` va escrita aquí, no leída del bean: si
 * alguien añade un listener a la proyección sin añadirlo al gauge, o mete en el gauge uno que no la toca, este test
 * lo dice. Cómo se mide el retraso lo cubre `PendingDeliveryLagIntegrationTest`.
 *
 * Asertos con cotas, nunca igualdades: `event_publication` la comparte toda la JVM de tests y puede haber entregas
 * reales en vuelo de otros tests. Solo se borra lo sembrado aquí.
 */
class PlanificacionMetricsIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var metrics: PlanificacionMetrics

    @Autowired private lateinit var meterRegistry: MeterRegistry

    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var outbox: PendingDeliverySeeder

    @BeforeEach
    fun prepara() {
        outbox = PendingDeliverySeeder(jdbc, MARCADOR_TEST)
    }

    @AfterEach
    fun limpiaLoSembrado() {
        outbox.cleanUp()
    }

    @Test
    fun `una entrega pendiente a cualquiera de los listeners de la proyeccion mueve el gauge`() {
        listOf(
            GroupMembersProjectionListener::class to MembresiaDeGrupoCambiada::class,
            GroupMembersProjectionListener::class to EntrenadorAsignadoAGrupo::class,
        ).forEach { (listener, event) ->
            withClue(listener.simpleName) {
                outbox.seed(listener, event, age = HOURS_OLD)

                metrics.membersProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds.toDouble()
            }
            outbox.cleanUp()
        }
    }

    @Test
    fun `una entrega pendiente a otro listener del modulo no mueve el gauge`() {
        outbox.seed(PlanificacionDeletionListener::class, AlumnoEliminado::class, age = DAY_OLD)

        metrics.membersProjectionLagSeconds() shouldBeLessThan DAY_OLD.seconds.toDouble()
    }

    /**
     * Contra el registro de Micrometer, no contra el bean: lo que consumen el scrape y la alarma es el **nombre** de
     * la métrica con sus tags, y un aserto sobre el bean pasaría igual con el nombre mal escrito o sin el tag
     * `projection`.
     */
    @Test
    fun `el gauge esta registrado con su nombre y sus tags`() {
        meterRegistry
            .find("planificacion.projection_lag_seconds")
            .tag("module", "planificacion")
            .tag("projection", "miembro_grupo")
            .gauge()
            .shouldNotBeNull()
    }

    private companion object {
        /** Muy por encima del umbral de 60 s y de cualquier entrega en vuelo, para no depender del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        val DAY_OLD: Duration = Duration.ofDays(1)

        const val MARCADOR_TEST = "sembradaPorPlanificacionMetricsIntegrationTest"
    }
}
