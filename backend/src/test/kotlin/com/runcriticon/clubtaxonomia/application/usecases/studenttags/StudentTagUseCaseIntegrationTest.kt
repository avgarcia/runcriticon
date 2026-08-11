package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.properties.Delegates

/**
 * Los cuatro casos de uso contra Postgres real, incluido el cruce con la supresión de personas: un alumno borrado deja
 * de poder clasificarse, que es la garantía que sostiene el derecho al olvido en este módulo.
 */
class StudentTagUseCaseIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var list: ListStudentTagsQuery

    @Autowired private lateinit var replace: ReplaceStudentTagsCommand

    @Autowired private lateinit var assign: AssignStudentTagCommand

    @Autowired private lateinit var unassign: UnassignStudentTagCommand

    @Autowired private lateinit var erasure: PersonErasure

    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    // Un club propio por test, nunca el de bootstrap. Estos tests crean ejes y valores, y el contenedor Postgres es
    // único para toda la JVM: sembrar en el club canónico le deja ejes de más a quien comprueba su taxonomía. JUnit
    // instancia la clase una vez por test, así que este id es distinto en cada uno y no hace falta limpiar entre ellos.
    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
    private val entrenador = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

    private var alumno: PersonId by Delegates.notNull()
    private var valorA: UUID by Delegates.notNull()
    private var valorB: UUID by Delegates.notNull()

    @BeforeEach
    fun prepara() {
        alumno = sembrarAlumno()
        val eje = sembrarEje()
        valorA = sembrarValor(eje, "primero")
        valorB = sembrarValor(eje, "segundo")
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `recorrido completo de clasificacion sobre la base de datos real`() {
        replace.execute(admin, alumno.value, listOf(valorA)).shouldBeRight()
        list
            .execute(admin, alumno.value)
            .shouldBeRight()
            .assigned
            .map { it.value.id.value } shouldBe listOf(valorA)

        assign.execute(admin, alumno.value, valorB).shouldBeRight()
        list
            .execute(admin, alumno.value)
            .shouldBeRight()
            .assigned
            .map { it.value.id.value } shouldBe
            listOf(valorA, valorB)

        unassign.execute(admin, alumno.value, valorA).shouldBeRight()
        list
            .execute(admin, alumno.value)
            .shouldBeRight()
            .assigned
            .map { it.value.id.value } shouldBe listOf(valorB)
    }

    @Test
    fun `el entrenador tambien puede clasificar`() {
        autenticar(entrenador)

        replace.execute(entrenador, alumno.value, listOf(valorA)).shouldBeRight()
    }

    /**
     * LAL-87 AC1: la pertenencia a un grupo vivo se actualiza sola porque `previewMembers` resuelve en caliente sobre
     * `alumno_tag` (ADR-0002 D3) — este test cruza esa garantía con los casos de uso reales de clasificación, no solo
     * con el SQL directo que ya cubre `GroupRepositoryIntegrationTest`.
     */
    @Test
    fun `cambiar los tags via el caso de uso actualiza quien esta en un grupo vivo con ese filtro`() {
        groups.previewMembers(club, setOf(TagValueId.of(valorA))).total shouldBe 0

        assign.execute(admin, alumno.value, valorA).shouldBeRight()
        groups.previewMembers(club, setOf(TagValueId.of(valorA))).total shouldBe 1

        unassign.execute(admin, alumno.value, valorA).shouldBeRight()
        groups.previewMembers(club, setOf(TagValueId.of(valorA))).total shouldBe 0
    }

    @Test
    fun `dos valores del mismo eje conviven en la base de datos`() {
        replace.execute(admin, alumno.value, listOf(valorA, valorB)).shouldBeRight()

        contarAsignaciones() shouldBe 2
    }

    /**
     * El cruce con el derecho al olvido: tras suprimir a la persona su fila desaparece de la proyección, así que
     * clasificarla deja de ser posible y no se pueden reescribir asignaciones de alguien ya borrado.
     */
    @Test
    fun `un alumno suprimido ya no se puede clasificar`() {
        replace.execute(admin, alumno.value, listOf(valorA)).shouldBeRight()

        erasure.erase(alumno)

        replace.execute(admin, alumno.value, listOf(valorB)).shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
        assign.execute(admin, alumno.value, valorB).shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
        list.execute(admin, alumno.value).shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
        contarAsignaciones().shouldBeZero()
    }

    @Test
    fun `clasificar a un entrenador no es posible`() {
        val coach = sembrarPersona("ENTRENADOR")

        replace.execute(admin, coach.value, listOf(valorA)).shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
    }

    @Test
    fun `una persona inexistente no se puede clasificar`() {
        val fantasma = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        replace.execute(admin, fantasma.value, listOf(valorA)).shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
    }

    @Test
    fun `un valor archivado despues de asignarse se conserva al volver a guardar`() {
        replace.execute(admin, alumno.value, listOf(valorA)).shouldBeRight()
        jdbc.update("UPDATE club_taxonomia.tag_value SET archivado_en = now() WHERE id = ?", valorA)

        replace
            .execute(admin, alumno.value, listOf(valorA))
            .shouldBeRight()
            .assigned
            .map { it.value.id.value } shouldBe
            listOf(valorA)
    }

    @Test
    fun `un valor archivado que no se tenia se rechaza`() {
        jdbc.update("UPDATE club_taxonomia.tag_value SET archivado_en = now() WHERE id = ?", valorA)

        assign.execute(admin, alumno.value, valorA).shouldBeLeft()
        list
            .execute(admin, alumno.value)
            .shouldBeRight()
            .assigned
            .shouldBeEmpty()
    }

    private fun Int.shouldBeZero() = this shouldBe 0

    private fun contarAsignaciones(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE alumno_id = ?",
            Int::class.java,
            alumno.value,
        ) ?: 0

    private fun sembrarAlumno(): PersonId = sembrarPersona("ALUMNO")

    private fun sembrarPersona(rol: String): PersonId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            "Persona $rol",
            "persona-$id@club.test",
            rol,
            UuidCreator.getTimeOrderedEpoch(),
        )
        return PersonId.of(id)
    }

    /** Sufijo tomado del final del UUID: los v7 comparten prefijo temporal y el nombre del eje es único por club. */
    private fun sembrarEje(): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            id,
            club.value,
            "eje-${id.toString().takeLast(SUFIJO_UNICO)}",
        )
        return id
    }

    private fun sembrarValor(
        keyId: UUID,
        nombre: String,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            id,
            keyId,
            club.value,
            nombre,
        )
        return id
    }

    private fun autenticar(principal: Principal) {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    private companion object {
        const val SUFIJO_UNICO = 8
    }
}
