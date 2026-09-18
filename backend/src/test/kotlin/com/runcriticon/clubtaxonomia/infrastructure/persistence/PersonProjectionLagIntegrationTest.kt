package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.infrastructure.observability.ClubTaxonomiaProjectionMetrics
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/**
 * `PersonProjection.lagSeconds()` agrega `MAX(last_processed_event_ts)` sobre **toda** la tabla
 * `club_taxonomia.persona`, sin filtrar por club ni por id (es un gauge de sistema sobre la proyección completa, no un
 * dato de cliente — ver KDoc de `PersonProjectionJdbc.lagSeconds`). Por eso, a diferencia del resto de
 * [PersonProjectionIntegrationTest] y [PersonErasureIntegrationTest], sus tests no se pueden aislar sembrando IDs
 * propios: cualquier fila que deje otro test en el contenedor compartido cambia el resultado. Esta clase vive sola,
 * con su propio `@BeforeEach` que vacía la tabla, y es la única a la que le hace falta.
 */
class PersonProjectionLagIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var projection: PersonProjection

    @Autowired private lateinit var metrics: ClubTaxonomiaProjectionMetrics

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun limpiaLaProyeccion() {
        jdbc.update("DELETE FROM club_taxonomia.persona")
    }

    @Test
    fun `una proyeccion vacia no esta retrasada`() {
        projection.lagSeconds() shouldBe 0L
    }

    @Test
    fun `el lag es la antiguedad del evento mas reciente aplicado y alimenta el gauge`() {
        val ancient = Instant.now().minusSeconds(ANCIENT_EVENT_AGE_SECONDS)
        projection.upsert(alumno(), UUID.randomUUID(), ancient)

        projection.lagSeconds() shouldBeGreaterThanOrEqualTo ANCIENT_EVENT_AGE_SECONDS
        metrics.personProjectionLagSeconds() shouldBeGreaterThanOrEqualTo ANCIENT_EVENT_AGE_SECONDS.toDouble()
    }

    /**
     * Contra el registro de Micrometer, no contra el bean de métricas: lo que consumen el scrape y la alarma de
     * proyección obsoleta es el **nombre** de la métrica con sus tags, y un aserto sobre el bean pasaría igual con el
     * nombre mal escrito o sin el tag `projection`.
     */
    @Test
    fun `el gauge del lag esta registrado con su nombre y sus tags`() {
        val gauge =
            meterRegistry
                .find("club_taxonomia.projection_lag_seconds")
                .tag("module", "club_taxonomia")
                .tag("projection", "persona")
                .gauge()

        gauge.shouldNotBeNull()
    }

    private fun alumno(
        id: PersonId = PersonId.of(UUID.randomUUID()),
        clubId: ClubId = ClubId.of(UUID.randomUUID()),
    ) = Person(
        id = id,
        clubId = clubId,
        name = "Beto Ruiz",
        email = "beto@club.test",
        role = PersonRole.ALUMNO,
        status = PersonStatus.INVITADO,
    )

    private companion object {
        /** Holgado respecto al umbral de 60 s de proyección obsoleta, para que el aserto no dependa del reloj. */
        const val ANCIENT_EVENT_AGE_SECONDS = 120L
    }
}
