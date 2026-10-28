package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.application.listeners.CoachGroupProjectionListener
import com.runcriticon.seguimiento.application.listeners.ConsentProjectionListener
import com.runcriticon.seguimiento.application.listeners.MarkPaceRecalculationListener
import com.runcriticon.seguimiento.application.listeners.PersonalizationProjectionListener
import com.runcriticon.seguimiento.application.listeners.ResolvedPlanProjectionListener
import com.runcriticon.seguimiento.application.listeners.SeguimientoDeletionListener
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.PendingDeliverySeeder
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import java.time.Duration
import kotlin.reflect.KClass

/**
 * Qué entregas mueven cada gauge `seguimiento.projection_lag_seconds` del módulo.
 *
 * La tabla de listeners va escrita aquí, no leída del bean: cada listener del módulo con la proyección en la que
 * escribe, o ninguna. Si alguien añade un listener a una proyección sin añadirlo a su gauge, mete en un gauge uno
 * que no la toca o crea un listener nuevo sin decidir a qué gauge pertenece, este test lo dice. Cómo se mide el
 * retraso lo cubre `PendingDeliveryLagIntegrationTest`.
 *
 * Asertos con cotas, nunca igualdades: `event_publication` la comparte toda la JVM de tests y puede haber entregas
 * reales en vuelo de otros tests, pero ninguna con horas de antigüedad. Solo se borra lo sembrado aquí.
 */
class SeguimientoProjectionMetricsIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var metrics: SeguimientoProjectionMetrics

    @Autowired private lateinit var meterRegistry: MeterRegistry

    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var outbox: PendingDeliverySeeder

    /** Valor de cada gauge del módulo, por su tag `projection`. */
    private val gauges: Map<String, () -> Double> by lazy {
        mapOf(
            "plan_resuelto_por_alumno" to { metrics.resolvedPlanLagSeconds() },
            "consentimiento_alumno" to { metrics.consentLagSeconds() },
            "grupo_entrenador" to { metrics.coachGroupLagSeconds() },
        )
    }

    @BeforeEach
    fun prepara() {
        outbox = PendingDeliverySeeder(jdbc, MARCADOR_TEST)
    }

    @AfterEach
    fun limpiaLoSembrado() {
        outbox.cleanUp()
    }

    @Test
    fun `cada entrega pendiente mueve solo el gauge de la proyeccion en la que escribe su listener`() {
        LISTENERS.forEach { (listener, event, projection) ->
            outbox.seed(listener, event, age = HOURS_OLD)

            gauges.forEach { (gauge, value) ->
                withClue("${listener.simpleName} -> $gauge") {
                    if (gauge == projection) {
                        value() shouldBeGreaterThanOrEqualTo HOURS_OLD.seconds.toDouble()
                    } else {
                        value() shouldBeLessThan HOURS_OLD.seconds.toDouble()
                    }
                }
            }
            outbox.cleanUp()
        }
    }

    /** Un listener nuevo del módulo tiene que entrar en la tabla, con su proyección o sin ninguna. */
    @Test
    fun `la tabla cubre todos los listeners del modulo`() {
        val declared =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.runcriticon.seguimiento")
                .filter { type -> type.methods.any { it.isAnnotatedWith(ApplicationModuleListener::class.java) } }
                .map { it.name }
                .toSet()

        LISTENERS.map { it.listener.java.name }.toSet() shouldBe declared
    }

    /**
     * Contra el registro de Micrometer, no contra el bean: lo que consumen el scrape y la alarma es el **nombre** de
     * la métrica con sus tags, y un aserto sobre el bean pasaría igual con el nombre mal escrito o sin el tag
     * `projection`.
     */
    @Test
    fun `cada gauge esta registrado con su nombre y sus tags`() {
        gauges.keys.forEach { projection ->
            withClue(projection) {
                meterRegistry
                    .find("seguimiento.projection_lag_seconds")
                    .tag("module", "seguimiento")
                    .tag("projection", projection)
                    .gauge()
                    .shouldNotBeNull()
            }
        }
    }

    /** Un listener del módulo, un evento que escucha y la proyección en la que escribe (`null`: ninguna). */
    private data class Writer(
        val listener: KClass<*>,
        val event: KClass<*>,
        val projection: String?,
    )

    private companion object {
        val LISTENERS =
            listOf(
                Writer(ResolvedPlanProjectionListener::class, PlanPublicado::class, "plan_resuelto_por_alumno"),
                Writer(
                    PersonalizationProjectionListener::class,
                    PersonalizacionAplicada::class,
                    "plan_resuelto_por_alumno",
                ),
                Writer(MarkPaceRecalculationListener::class, MarcaActualizada::class, "plan_resuelto_por_alumno"),
                Writer(ConsentProjectionListener::class, ConsentimientoConcedido::class, "consentimiento_alumno"),
                Writer(CoachGroupProjectionListener::class, EntrenadorAsignadoAGrupo::class, "grupo_entrenador"),
                Writer(SeguimientoDeletionListener::class, AlumnoEliminado::class, null),
            )

        /** Muy por encima del umbral de 60 s y de cualquier entrega en vuelo, para no depender del reloj. */
        val HOURS_OLD: Duration = Duration.ofHours(2)

        const val MARCADOR_TEST = "sembradaPorSeguimientoProjectionMetricsIntegrationTest"
    }
}
