package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/**
 * El borrado contra Postgres real: alcanza a las dos tablas, deja lápida y la lápida bloquea escrituras posteriores.
 * Es lo único que puede verificarlo, porque todo vive en SQL.
 */
class PersonErasureIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var erasure: PersonErasure

    @Autowired private lateinit var projection: PersonProjection

    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun limpia() {
        jdbc.update("DELETE FROM club_taxonomia.alumno_tag")
        jdbc.update("DELETE FROM club_taxonomia.persona")
        jdbc.update("DELETE FROM club_taxonomia.persona_eliminada")
    }

    @Test
    fun `borrar a una persona elimina su proyeccion y sus asignaciones de tag`() {
        val person = proyectarAlumno()
        asignarleUnTag(person.id)

        val erased = erasure.erase(person.id)

        erased.projections shouldBe 1
        erased.tagAssignments shouldBe 1
        contarPersonas(person.id) shouldBe 0
        contarTags(person.id) shouldBe 0
    }

    @Test
    fun `el borrado deja lapida`() {
        val person = proyectarAlumno()

        erasure.erase(person.id)

        contarLapidas(person.id.value) shouldBe 1
    }

    @Test
    fun `repetir el borrado no falla ni duplica la lapida`() {
        val person = proyectarAlumno()
        erasure.erase(person.id)

        val segundo = erasure.erase(person.id)

        segundo.projections shouldBe 0
        contarLapidas(person.id.value) shouldBe 1
    }

    @Test
    fun `borrar a alguien que este modulo nunca proyecto deja igualmente la lapida`() {
        val nunkaProyectada = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        val erased = erasure.erase(nunkaProyectada)

        erased.projections shouldBe 0
        contarLapidas(nunkaProyectada.value) shouldBe 1
    }

    /**
     * El caso que justifica la lápida: un evento de alta **posterior** al borrado no puede resucitar a la persona. Con
     * un `occurredAt` anterior lo explicaría también la guarda de orden; con uno posterior, solo la lápida.
     */
    @Test
    fun `una escritura posterior al borrado no resucita a la persona`() {
        val person = proyectarAlumno()
        erasure.erase(person.id)

        val aplicado =
            projection.upsert(
                person,
                UuidCreator.getTimeOrderedEpoch(),
                Instant.now().plusSeconds(TREINTA_DIAS),
            )

        aplicado shouldBe false
        contarPersonas(person.id) shouldBe 0
    }

    private fun proyectarAlumno(): Person {
        val person =
            Person(
                id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                clubId = ClubId.of(UuidCreator.getTimeOrderedEpoch()),
                name = "Beto Ruiz",
                email = "beto@club.test",
                role = PersonRole.ALUMNO,
                status = PersonStatus.ACTIVO,
            )
        projection.upsert(person, UuidCreator.getTimeOrderedEpoch(), Instant.now()) shouldBe true
        return person
    }

    /** `alumno_tag.tag_value_id` tiene clave ajena real: hay que colgarse de un valor de taxonomía existente. */
    private fun asignarleUnTag(personId: PersonId) {
        val tagValueId =
            jdbc.queryForObject(
                "SELECT id FROM club_taxonomia.tag_value LIMIT 1",
                UUID::class.java,
            )
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            UuidCreator.getTimeOrderedEpoch(),
            personId.value,
            tagValueId,
        )
    }

    private fun contarPersonas(id: PersonId): Int = contarPersonas(id.value)

    private fun contarPersonas(id: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM club_taxonomia.persona WHERE id = ?", Int::class.java, id) ?: 0

    private fun contarTags(id: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE alumno_id = ?",
            Int::class.java,
            id.value,
        ) ?: 0

    private fun contarLapidas(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.persona_eliminada WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    private companion object {
        const val TREINTA_DIAS = 2_592_000L
    }
}
