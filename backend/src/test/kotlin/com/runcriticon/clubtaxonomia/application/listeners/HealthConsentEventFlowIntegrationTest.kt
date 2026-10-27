package com.runcriticon.clubtaxonomia.application.listeners

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * [HealthConsentListener] de punta a punta: eventos reales por el outbox, sin `SecurityContext` (como en producción),
 * contra los repositorios reales con `@AuthScope`. Club propio por test, con su propio eje `estado`.
 */
class HealthConsentEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var erasure: PersonErasure

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val alumno = UuidCreator.getTimeOrderedEpoch()
    private var lesion: UUID = UUID.randomUUID()

    @BeforeEach
    fun prepara() {
        val estado = sembrarEje("estado")
        lesion = sembrarValor(estado, "lesión", datoSalud = true, rol = "LESION")
        sembrarAlumno(alumno)
    }

    @Test
    fun `revocar retira todos los valores de salud y deja el resto de la clasificacion`() {
        val otroEje = sembrarEje("nivel")
        val medio = sembrarValor(otroEje, "medio")
        val postParto = sembrarValor(sembrarEje("maternidad"), "post-parto", datoSalud = true)
        asignar(lesion)
        asignar(postParto)
        asignar(medio)
        sembrarConsentimiento(vigente = true, procesadoEn = Instant.now().minusSeconds(3600))

        publicar(revocado(occurredAt = Instant.now()))

        awaitVigente(false)
        awaitTags(setOf(medio)) shouldBe true
    }

    /** El valor se reconoce por su marcador: renombrarlo no impide retirarlo. */
    @Test
    fun `revocar retira el valor de lesion aunque el admin lo haya renombrado`() {
        jdbc.update("UPDATE club_taxonomia.tag_value SET nombre = 'de baja' WHERE id = ?", lesion)
        asignar(lesion)
        sembrarConsentimiento(vigente = true, procesadoEn = Instant.now().minusSeconds(3600))

        publicar(revocado(occurredAt = Instant.now()))

        awaitVigente(false)
        awaitTags(emptySet()) shouldBe true
    }

    /** Una revocación rezagada, tras una concesión posterior, no cambia el estado ni quita el tag. */
    @Test
    fun `una revocacion anterior a la ultima concesion no retira el tag`() {
        asignar(lesion)
        sembrarConsentimiento(vigente = true, procesadoEn = Instant.now())
        val tarde = revocado(occurredAt = Instant.now().minusSeconds(3600))

        publicar(tarde)
        awaitProcesado(tarde.eventId)

        vigente() shouldBe true
        tags() shouldBe setOf(lesion)
    }

    @Test
    fun `conceder proyecta el consentimiento vigente`() {
        publicar(concedido())

        awaitVigente(true)
    }

    @Test
    fun `un alumno suprimido no reaparece en la proyeccion por un evento rezagado`() {
        erasure.erase(PersonId.of(alumno))
        val rezagado = concedido()

        publicar(rezagado)
        awaitProcesado(rezagado.eventId)

        vigente().shouldBeNull()
    }

    @Test
    fun `suprimir al alumno borra su consentimiento proyectado`() {
        sembrarConsentimiento(vigente = true, procesadoEn = Instant.now())

        erasure.erase(PersonId.of(alumno))

        vigente().shouldBeNull()
    }

    private fun revocado(occurredAt: Instant) =
        ConsentimientoRevocado(
            eventId = UUID.randomUUID(),
            aggregateId = alumno,
            occurredAt = occurredAt,
            clubId = club.value,
            actorId = alumno,
            traceparent = null,
        )

    private fun concedido() =
        ConsentimientoConcedido(
            eventId = UUID.randomUUID(),
            aggregateId = alumno,
            occurredAt = Instant.now(),
            clubId = club.value,
            actorId = alumno,
            traceparent = null,
            versionTexto = "v2026-08-25",
        )

    private fun publicar(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun vigente(): Boolean? =
        jdbc
            .queryForList(
                "SELECT vigente FROM club_taxonomia.consentimiento_alumno WHERE alumno_id = ?",
                Boolean::class.java,
                alumno,
            ).firstOrNull()

    private fun tags(): Set<UUID> =
        jdbc
            .queryForList(
                "SELECT tag_value_id FROM club_taxonomia.alumno_tag WHERE alumno_id = ?",
                UUID::class.java,
                alumno,
            ).filterNotNull()
            .toSet()

    private fun awaitVigente(esperado: Boolean) {
        await("consentimiento_alumno.vigente = $esperado") { vigente() == esperado }
    }

    private fun awaitTags(esperados: Set<UUID>): Boolean {
        await("tags = $esperados") { tags() == esperados }
        return true
    }

    private fun awaitProcesado(eventId: UUID) {
        await("HealthConsentListener completa $eventId") {
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? " +
                    "AND listener_id LIKE '%HealthConsentListener%' AND completion_date IS NOT NULL",
                Int::class.java,
                "%$eventId%",
            ) == 1
        }
    }

    private fun await(
        que: String,
        condicion: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            if (condicion()) return
            Thread.sleep(25)
        }
        throw AssertionError("No se cumplió en 5s: $que")
    }

    private fun asignar(valor: UUID) {
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            club.value,
            alumno,
            valor,
        )
    }

    private fun sembrarConsentimiento(
        vigente: Boolean,
        procesadoEn: Instant,
    ) {
        jdbc.update(
            """
            INSERT INTO club_taxonomia.consentimiento_alumno
                (alumno_id, club_id, vigente, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            alumno,
            club.value,
            vigente,
            UUID.randomUUID(),
            Timestamp.from(procesadoEn),
        )
    }

    private fun sembrarAlumno(id: UUID) {
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, 'ALUMNO', 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            "Alumno $id",
            "alumno-$id@club.test",
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    private fun sembrarEje(nombre: String): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        return id
    }

    private fun sembrarValor(
        keyId: UUID,
        nombre: String,
        datoSalud: Boolean = false,
        rol: String? = null,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre, dato_salud, rol_reservado) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            id,
            keyId,
            club.value,
            nombre,
            datoSalud,
            rol,
        )
        return id
    }
}
