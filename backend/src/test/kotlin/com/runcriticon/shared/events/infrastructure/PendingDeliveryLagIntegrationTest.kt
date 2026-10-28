package com.runcriticon.shared.events.infrastructure

import com.runcriticon.shared.events.PendingDeliveryLag
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.PendingDeliverySeeder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration

/**
 * [PendingDeliveryLag] contra el outbox real. Los listeners son clases de este fichero que no escuchan nada, así que
 * ninguna entrega real ni ningún otro test puede dejar filas con su prefijo: lo que se mide es solo lo sembrado aquí.
 */
class PendingDeliveryLagIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var lag: PendingDeliveryLag

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
    fun `sin entregas pendientes el lag es 0`() {
        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBe 0L
    }

    @Test
    fun `una entrega pendiente cuenta con su antiguedad`() {
        outbox.seed(ProjectionWriter::class, SomeEvent::class, age = HOURS_OLD)

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
    }

    /** Una `FAILED` no converge sola: el gauge tiene que seguir en rojo hasta que se reprocese. */
    @Test
    fun `una entrega FAILED sigue contando`() {
        outbox.seed(ProjectionWriter::class, SomeEvent::class, age = HOURS_OLD, status = "FAILED")

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
    }

    @Test
    fun `una entrega completada no cuenta`() {
        outbox.seed(ProjectionWriter::class, SomeEvent::class, age = DAY_OLD, completed = true)

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBe 0L
    }

    @Test
    fun `una entrega a otro listener no cuenta`() {
        outbox.seed(UnrelatedListener::class, SomeEvent::class, age = DAY_OLD)

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBe 0L
    }

    /** El punto final del prefijo: `ProjectionWriterV2` empieza igual que `ProjectionWriter`, pero es otra clase. */
    @Test
    fun `una clase cuyo nombre empieza igual no cuenta`() {
        outbox.seed(ProjectionWriterV2::class, SomeEvent::class, age = DAY_OLD)

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBe 0L
    }

    /** No se da por hecho que el método se llame `on`: el prefijo es la clase. */
    @Test
    fun `cuenta cualquier metodo del listener`() {
        outbox.seed(
            listenerId = "${ProjectionWriter::class.java.name}.handle(${SomeEvent::class.java.name})",
            eventType = SomeEvent::class.java.name,
            age = HOURS_OLD,
        )

        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds
    }

    @Test
    fun `con varios listeners manda la entrega mas antigua de cualquiera de ellos`() {
        outbox.seed(ProjectionWriter::class, SomeEvent::class, age = HOURS_OLD)
        outbox.seed(OtherProjectionWriter::class, SomeEvent::class, age = DAY_OLD)

        val both = setOf(ProjectionWriter::class, OtherProjectionWriter::class)
        lag.oldestPendingSeconds(both) shouldBeGreaterThanOrEqualTo DAY_OLD.seconds
        lag.oldestPendingSeconds(setOf(ProjectionWriter::class)) shouldBeLessThan DAY_OLD.seconds
    }

    /** Como `@Repository`, Spring traduce el `require` a su jerarquía de acceso a datos; la causa es la original. */
    @Test
    fun `sin listeners rechaza la consulta en vez de medir el outbox entero`() {
        val rejection = shouldThrow<InvalidDataAccessApiUsageException> { lag.oldestPendingSeconds(emptySet()) }

        rejection.cause.shouldBeInstanceOf<IllegalArgumentException>()
    }

    private companion object {
        /** Muy por encima del umbral de 60 s y de cualquier deriva del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        val DAY_OLD: Duration = Duration.ofDays(1)

        const val MARCADOR_TEST = "sembradaPorPendingDeliveryLagIntegrationTest"
    }
}

private class ProjectionWriter

private class ProjectionWriterV2

private class OtherProjectionWriter

private class UnrelatedListener

private class SomeEvent
