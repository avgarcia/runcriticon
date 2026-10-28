package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.listeners.HealthConsentListener
import com.runcriticon.clubtaxonomia.application.listeners.MergeSuggestionListener
import com.runcriticon.clubtaxonomia.application.listeners.PersonProjectionListener
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
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
 * Qué entregas mueven el gauge `club_taxonomia.projection_lag_seconds`.
 *
 * La lista de listeners que escriben en `persona` va escrita aquí, no leída del bean: si
 * alguien añade un listener a la proyección sin añadirlo al gauge, o mete en el gauge uno que no la toca, este test
 * lo dice. Cómo se mide el retraso lo cubre `PendingDeliveryLagIntegrationTest`.
 *
 * Asertos con cotas, nunca igualdades: `event_publication` la comparte toda la JVM de tests y puede haber entregas
 * reales en vuelo de otros tests. Solo se borra lo sembrado aquí.
 */
class ClubTaxonomiaProjectionMetricsIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var metrics: ClubTaxonomiaProjectionMetrics

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
            PersonProjectionListener::class to AlumnoInvitado::class,
        ).forEach { (listener, event) ->
            withClue(listener.simpleName) {
                outbox.seed(listener, event, age = HOURS_OLD)

                metrics.personProjectionLagSeconds() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds.toDouble()
            }
            outbox.cleanUp()
        }
    }

    @Test
    fun `una entrega pendiente a otro listener del modulo no mueve el gauge`() {
        outbox.seed(MergeSuggestionListener::class, MembresiaDeGrupoCambiada::class, age = DAY_OLD)
        outbox.seed(HealthConsentListener::class, ConsentimientoConcedido::class, age = DAY_OLD)

        metrics.personProjectionLagSeconds() shouldBeLessThan DAY_OLD.seconds.toDouble()
    }

    /**
     * Contra el registro de Micrometer, no contra el bean: lo que consumen el scrape y la alarma es el **nombre** de
     * la métrica con sus tags, y un aserto sobre el bean pasaría igual con el nombre mal escrito o sin el tag
     * `projection`.
     */
    @Test
    fun `el gauge esta registrado con su nombre y sus tags`() {
        meterRegistry
            .find("club_taxonomia.projection_lag_seconds")
            .tag("module", "club_taxonomia")
            .tag("projection", "persona")
            .gauge()
            .shouldNotBeNull()
    }

    private companion object {
        /** Muy por encima del umbral de 60 s y de cualquier entrega en vuelo, para no depender del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        val DAY_OLD: Duration = Duration.ofDays(1)

        const val MARCADOR_TEST = "sembradaPorClubTaxonomiaProjectionMetricsIntegrationTest"
    }
}
