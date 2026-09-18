package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/**
 * Contrato del adaptador de la proyección contra un Postgres real: es la única forma de verificar la guarda de orden y
 * la idempotencia, porque las dos viven en SQL (el `WHERE` del `ON CONFLICT DO UPDATE` y la clave primaria de
 * `evento_procesado`), no en Kotlin.
 *
 * Sin `@BeforeEach` que vacíe tablas: el contenedor Postgres es *singleton* para toda la JVM de test (ver KDoc de
 * [IntegrationTestBase]), compartido con el resto de clases del módulo. Cada test siembra su propia persona con un
 * `id`/`clubId` aleatorio y filtra por `id`, así que no le importa lo que dejen otros tests ni otras clases.
 * Excepción: el gauge `lagSeconds()` agrega `MAX(last_processed_event_ts)` sin filtrar por nada — sus tests
 * viven en [PersonProjectionLagIntegrationTest], que sí necesita (y declara) exclusividad sobre la tabla.
 */
class PersonProjectionIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var projection: PersonProjection

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private lateinit var processedEvents: ProcessedEventTracker

    /**
     * La guarda de supresión, probada desde el lado de la escritura: con lápida no se inserta nada, por muy reciente
     * que sea el evento. Es lo que impide que un alta rezagada resucite a quien ejerció su derecho al olvido.
     */
    @Test
    fun `una persona con lapida no vuelve a materializarse`() {
        val person = alumno()
        jdbc.update("INSERT INTO club_taxonomia.persona_eliminada (id) VALUES (?)", person.id.value)

        val aplicado = projection.upsert(person, UUID.randomUUID(), Instant.now())

        aplicado shouldBe false
        countPersons(person.id) shouldBe 0
    }

    @Test
    fun `un alumno nuevo se inserta con su rol, estado y el evento que lo trajo`() {
        val person = alumno()
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-07-30T10:00:00Z")

        projection.upsert(person, eventId, occurredAt) shouldBe true

        val row = readPerson(person.id)
        row["club_id"] shouldBe person.clubId.value
        row["nombre"] shouldBe "Beto Ruiz"
        row["email"] shouldBe "beto@club.test"
        row["rol"] shouldBe "ALUMNO"
        row["estado"] shouldBe "INVITADO"
        row["last_processed_event_id"] shouldBe eventId
    }

    @Test
    fun `un evento posterior actualiza la persona y el evento que la deja`() {
        val person = alumno()
        projection.upsert(person, UUID.randomUUID(), Instant.parse("2026-07-30T10:00:00Z"))

        val activationId = UUID.randomUUID()
        val activated =
            projection.upsert(
                person.copy(status = PersonStatus.ACTIVO),
                activationId,
                Instant.parse("2026-07-30T11:00:00Z"),
            )

        activated shouldBe true
        val row = readPerson(person.id)
        row["estado"] shouldBe "ACTIVO"
        row["last_processed_event_id"] shouldBe activationId
    }

    @Test
    fun `un evento anterior al ya aplicado se descarta y no revierte el estado`() {
        val person = alumno()
        val activationId = UUID.randomUUID()
        val activation = Instant.parse("2026-07-30T11:00:00Z")
        projection.upsert(person.copy(status = PersonStatus.ACTIVO), activationId, activation)

        // La invitación es anterior a la activación: es el desorden de entrega que la guarda tiene que absorber.
        val applied =
            projection.upsert(
                person.copy(status = PersonStatus.INVITADO),
                UUID.randomUUID(),
                Instant.parse("2026-07-30T10:00:00Z"),
            )

        applied shouldBe false
        val row = readPerson(person.id)
        row["estado"] shouldBe "ACTIVO"
        row["last_processed_event_id"] shouldBe activationId
    }

    @Test
    fun `dos eventos con el mismo instante no se descartan entre si`() {
        val person = alumno()
        val sameInstant = Instant.parse("2026-07-30T10:00:00Z")
        projection.upsert(person, UUID.randomUUID(), sameInstant)

        projection.upsert(person.copy(status = PersonStatus.ACTIVO), UUID.randomUUID(), sameInstant) shouldBe true

        readPerson(person.id)["estado"] shouldBe "ACTIVO"
    }

    @Test
    fun `reaplicar el mismo evento deja la fila igual`() {
        val person = alumno()
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-07-30T10:00:00Z")
        projection.upsert(person, eventId, occurredAt)

        projection.upsert(person, eventId, occurredAt) shouldBe true

        countPersons(person.id) shouldBe 1
        readPerson(person.id)["last_processed_event_id"] shouldBe eventId
    }

    @Test
    fun `el tracker acepta un evento una sola vez por listener`() {
        val eventId = UUID.randomUUID()

        processedEvents.markIfNew("PersonProjectionListener", eventId) shouldBe true
        processedEvents.markIfNew("PersonProjectionListener", eventId) shouldBe false
    }

    @Test
    fun `el mismo evento se procesa una vez por cada listener distinto`() {
        val eventId = UUID.randomUUID()

        processedEvents.markIfNew("PersonProjectionListener", eventId) shouldBe true
        processedEvents.markIfNew("OtroListener", eventId) shouldBe true
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

    private fun readPerson(id: PersonId): Map<String, Any?> =
        jdbc.queryForMap("SELECT * FROM club_taxonomia.persona WHERE id = ?", id.value)

    private fun countPersons(id: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.persona WHERE id = ?",
            Int::class.java,
            id.value,
        ) ?: 0
}
