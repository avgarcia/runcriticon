package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.application.listeners.CoachGroupProjectionListener
import com.runcriticon.seguimiento.application.listeners.ConsentProjectionListener
import com.runcriticon.seguimiento.application.listeners.MarkPaceRecalculationListener
import com.runcriticon.seguimiento.application.listeners.PersonalizationProjectionListener
import com.runcriticon.seguimiento.application.listeners.ResolvedPlanProjectionListener
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
 * Qué entregas mueven el gauge `seguimiento.projection_lag_seconds`.
 *
 * La lista de listeners que escriben en `plan_resuelto_por_alumno` va escrita aquí, no leída del bean: si
 * alguien añade un listener a la proyección sin añadirlo al gauge, o mete en el gauge uno que no la toca, este test
 * lo dice. Cómo se mide el retraso lo cubre `PendingDeliveryLagIntegrationTest`.
 *
 * Asertos con cotas, nunca igualdades: `event_publication` la comparte toda la JVM de tests y puede haber entregas
 * reales en vuelo de otros tests. Solo se borra lo sembrado aquí.
 */
class SeguimientoProjectionMetricsIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var metrics: SeguimientoProjectionMetrics

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
            ResolvedPlanProjectionListener::class to PlanPublicado::class,
            PersonalizationProjectionListener::class to PersonalizacionAplicada::class,
            MarkPaceRecalculationListener::class to MarcaActualizada::class,
        ).forEach { (listener, event) ->
            withClue(listener.simpleName) {
                outbox.seed(listener, event, age = HOURS_OLD)

                metrics.resolvedPlanLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds.toDouble()
            }
            outbox.cleanUp()
        }
    }

    @Test
    fun `una entrega pendiente a otro listener del modulo no mueve el gauge`() {
        outbox.seed(CoachGroupProjectionListener::class, EntrenadorAsignadoAGrupo::class, age = DAY_OLD)
        outbox.seed(ConsentProjectionListener::class, ConsentimientoConcedido::class, age = DAY_OLD)

        metrics.resolvedPlanLagSeconds() shouldBeLessThan DAY_OLD.seconds.toDouble()
    }

    /**
     * Contra el registro de Micrometer, no contra el bean: lo que consumen el scrape y la alarma es el **nombre** de
     * la métrica con sus tags, y un aserto sobre el bean pasaría igual con el nombre mal escrito o sin el tag
     * `projection`.
     */
    @Test
    fun `el gauge esta registrado con su nombre y sus tags`() {
        meterRegistry
            .find("seguimiento.projection_lag_seconds")
            .tag("module", "seguimiento")
            .tag("projection", "plan_resuelto_por_alumno")
            .gauge()
            .shouldNotBeNull()
    }

    private companion object {
        /** Muy por encima del umbral de 60 s y de cualquier entrega en vuelo, para no depender del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        val DAY_OLD: Duration = Duration.ofDays(1)

        const val MARCADOR_TEST = "sembradaPorSeguimientoProjectionMetricsIntegrationTest"
    }
}
