package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.nulls.shouldBeNull
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

    @Autowired private lateinit var auditTrail: AuditTrail

    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun limpia() {
        jdbc.update("DELETE FROM club_taxonomia.alumno_tag")
        jdbc.update("DELETE FROM club_taxonomia.grupo_alumno_override")
        jdbc.update("DELETE FROM club_taxonomia.grupo_entrenador")
        jdbc.update("DELETE FROM club_taxonomia.grupo_tag_requerido")
        jdbc.update("DELETE FROM club_taxonomia.grupo")
        jdbc.update("DELETE FROM club_taxonomia.persona")
        jdbc.update("DELETE FROM club_taxonomia.persona_eliminada")
        jdbc.update("DELETE FROM club_taxonomia.evento_auditoria")
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

    /**
     * Sin este borrado, una inclusión manual sobrevive a su dueño: la rama de inclusiones de la resolución de membresía
     * no mira la lápida, así que seguiría metiendo en el grupo a alguien que ya ejerció su derecho de supresión.
     */
    @Test
    fun `borrar a una persona elimina sus excepciones manuales de grupo`() {
        val person = proyectarAlumno()
        val grupo = crearGrupo(person.clubId)
        incluirlaManualmente(grupo, person)

        val erased = erasure.erase(person.id)

        erased.groupOverrides shouldBe 1
        contarOverrides(person.id) shouldBe 0
    }

    /**
     * Simétrico del test de overrides: un entrenador suprimido que siguiera en `grupo_entrenador` aparecería
     * "llevando" un grupo sin existir en la proyección.
     */
    @Test
    fun `borrar a un entrenador elimina sus asignaciones a grupos`() {
        val entrenador = proyectarEntrenador()
        val grupo = crearGrupo(entrenador.clubId)
        asignarleUnGrupo(grupo, entrenador)

        val erased = erasure.erase(entrenador.id)

        erased.groupCoachAssignments shouldBe 1
        contarAsignaciones(entrenador.id) shouldBe 0
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
     * LAL-124: el asiento sobrevive (es anonimización, no borrado — ADR-0014 D6 categoría 2), pero deja de
     * identificar al alumno suprimido.
     */
    @Test
    fun `borrar a una persona anonimiza los asientos de auditoria donde es el sujeto`() {
        val alumno = proyectarAlumno()
        val entrenador = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(actorId = entrenador, sujetoId = alumno.id.value, clubId = alumno.clubId.value)

        val anonimizados = auditTrail.anonymize(alumno.id.value)

        anonimizados shouldBe 1
        contarTodosLosAsientos() shouldBe 1
        val asiento = leerUnicoAsiento()
        asiento.sujetoId.shouldBeNull()
        asiento.actorId shouldBe entrenador
    }

    /**
     * El caso que justifica el `CASE` por columna: anonimizar al actor (el entrenador que clasificó) no debe
     * despojar el `sujeto_id` de un alumno que no ha pedido nada.
     */
    @Test
    fun `borrar a un entrenador anonimiza solo su actor_id, sin tocar el sujeto`() {
        val entrenador = proyectarEntrenador()
        val alumno = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(actorId = entrenador.id.value, sujetoId = alumno, clubId = entrenador.clubId.value)

        val anonimizados = auditTrail.anonymize(entrenador.id.value)

        anonimizados shouldBe 1
        val asiento = leerUnicoAsiento()
        asiento.actorId.shouldBeNull()
        asiento.sujetoId shouldBe alumno
    }

    @Test
    fun `un asiento que no menciona al suprimido no se toca`() {
        val alumno = proyectarAlumno()
        val ajenoActor = UuidCreator.getTimeOrderedEpoch()
        val ajenoSujeto = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(actorId = ajenoActor, sujetoId = ajenoSujeto, clubId = alumno.clubId.value)

        val anonimizados = auditTrail.anonymize(alumno.id.value)

        anonimizados shouldBe 0
        val asiento = leerUnicoAsiento()
        asiento.actorId shouldBe ajenoActor
        asiento.sujetoId shouldBe ajenoSujeto
    }

    @Test
    fun `repetir la anonimizacion de auditoria no falla y no vuelve a tocar filas`() {
        val alumno = proyectarAlumno()
        sembrarAsiento(
            actorId = UuidCreator.getTimeOrderedEpoch(),
            sujetoId = alumno.id.value,
            clubId = alumno.clubId.value,
        )
        auditTrail.anonymize(alumno.id.value)

        val segunda = auditTrail.anonymize(alumno.id.value)

        segunda shouldBe 0
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

    private fun proyectarEntrenador(): Person {
        val person =
            Person(
                id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                clubId = ClubId.of(UuidCreator.getTimeOrderedEpoch()),
                name = "Carlos Ruiz",
                email = "carlos@club.test",
                role = PersonRole.ENTRENADOR,
                status = PersonStatus.ACTIVO,
            )
        projection.upsert(person, UuidCreator.getTimeOrderedEpoch(), Instant.now()) shouldBe true
        return person
    }

    private fun crearGrupo(clubId: ClubId): UUID {
        val grupoId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)",
            grupoId,
            clubId.value,
            "Ritmo alto",
        )
        return grupoId
    }

    private fun incluirlaManualmente(
        grupoId: UUID,
        person: Person,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_alumno_override (grupo_id, club_id, alumno_id, incluido) " +
                "VALUES (?, ?, ?, TRUE)",
            grupoId,
            person.clubId.value,
            person.id.value,
        )
    }

    private fun asignarleUnGrupo(
        grupoId: UUID,
        entrenador: Person,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_entrenador (grupo_id, club_id, entrenador_id) VALUES (?, ?, ?)",
            grupoId,
            entrenador.clubId.value,
            entrenador.id.value,
        )
    }

    private fun contarAsignaciones(id: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.grupo_entrenador WHERE entrenador_id = ?",
            Int::class.java,
            id.value,
        ) ?: 0

    private fun contarPersonas(id: PersonId): Int = contarPersonas(id.value)

    private fun contarPersonas(id: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM club_taxonomia.persona WHERE id = ?", Int::class.java, id) ?: 0

    private fun contarTags(id: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE alumno_id = ?",
            Int::class.java,
            id.value,
        ) ?: 0

    private fun contarOverrides(id: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.grupo_alumno_override WHERE alumno_id = ?",
            Int::class.java,
            id.value,
        ) ?: 0

    private fun contarLapidas(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.persona_eliminada WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    /**
     * SQL plano, no `auditTrail.record`: ese método lleva `@AuthScope(Scope.CLUB)` y este test no autentica ningún
     * `Principal` (verifica el listener de bajas, que no tiene uno) — mismo criterio que el resto de helpers de
     * siembra de este fichero.
     */
    private fun sembrarAsiento(
        actorId: UUID,
        sujetoId: UUID,
        clubId: UUID,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.evento_auditoria (id, club_id, tipo, actor_id, sujeto_id, ts) " +
                "VALUES (?, ?, 'TAGS_ALUMNO_ACTUALIZADOS', ?, ?, now())",
            UuidCreator.getTimeOrderedEpoch(),
            clubId,
            actorId,
            sujetoId,
        )
    }

    /** Cuenta filas totales, no por id: tras anonimizar, el id buscado ya no está en la fila que se quiere contar. */
    private fun contarTodosLosAsientos(): Int =
        jdbc.queryForObject("SELECT count(*) FROM club_taxonomia.evento_auditoria", Int::class.java) ?: 0

    /** Cada test siembra un único asiento tras limpiar la tabla en `@BeforeEach`: no hace falta filtrar. */
    private fun leerUnicoAsiento(): AsientoAuditoria =
        jdbc.queryForObject(
            "SELECT actor_id, sujeto_id FROM club_taxonomia.evento_auditoria",
            {
                rs,
                _,
                ->
                AsientoAuditoria(
                    actorId = rs.getObject("actor_id", UUID::class.java),
                    sujetoId = rs.getObject("sujeto_id", UUID::class.java),
                )
            },
        )

    private data class AsientoAuditoria(
        val actorId: UUID?,
        val sujetoId: UUID?,
    )

    private companion object {
        const val TREINTA_DIAS = 2_592_000L
    }
}
