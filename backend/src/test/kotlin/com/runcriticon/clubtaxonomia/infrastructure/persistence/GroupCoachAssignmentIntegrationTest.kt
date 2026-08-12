package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
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
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * `assignCoach`/`unassignCoach`/`findCoaches` contra Postgres real. Fichero aparte de
 * `GroupRepositoryIntegrationTest` (que ya cubre `resolveMembers`/overrides/listado/detalle) para no convertir esa
 * clase en un monolito -- `LargeClass` de detekt lo habría rechazado igual.
 */
class GroupCoachAssignmentIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    // Club propio por test, nunca el de bootstrap: el contenedor Postgres es único para toda la JVM.
    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    @BeforeEach
    fun prepara() {
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `asignar un entrenador lo deja en findCoaches, y repetirlo es idempotente`() {
        val entrenador = sembrarPersona("ENTRENADOR", nombre = "Carlos Ruiz")
        val grupo = crearGrupo("Con entrenador")

        asignarEntrenador(grupo, entrenador)
        asignarEntrenador(grupo, entrenador)

        entrenadores(grupo).map { it.id } shouldBe listOf(entrenador)
    }

    @Test
    fun `asignar un entrenador en un grupo de otro club no escribe nada`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val grupoDeOtroClub = crearGrupo("Grupo ajeno", club = otroClub)
        val entrenador = sembrarPersona("ENTRENADOR")

        asignarEntrenador(grupoDeOtroClub, entrenador)

        contarAsignaciones(grupoDeOtroClub, entrenador) shouldBe 0
    }

    @Test
    fun `quitar la asignacion borra la fila y quitarla de nuevo no falla`() {
        val entrenador = sembrarPersona("ENTRENADOR")
        val grupo = crearGrupo("Con entrenador")
        asignarEntrenador(grupo, entrenador)

        quitarEntrenador(grupo, entrenador) shouldBe 1

        entrenadores(grupo).shouldBeEmpty()
        quitarEntrenador(grupo, entrenador) shouldBe 0
    }

    @Test
    fun `quitar la asignacion de un grupo de otro club no borra nada`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val grupoDeOtroClub = crearGrupo("Grupo ajeno", club = otroClub)
        val entrenador = sembrarPersona("ENTRENADOR", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_entrenador (grupo_id, club_id, entrenador_id) VALUES (?, ?, ?)",
            grupoDeOtroClub.value,
            otroClub.value,
            entrenador.value,
        )

        quitarEntrenador(grupoDeOtroClub, entrenador) shouldBe 0

        contarAsignaciones(grupoDeOtroClub, entrenador) shouldBe 1
    }

    @Test
    fun `findCoaches ordena por nombre y no devuelve a los alumnos`() {
        val grupo = crearGrupo("Con varios entrenadores")
        val zoe = sembrarPersona("ENTRENADOR", nombre = "Zoe Martín")
        val ana = sembrarPersona("ENTRENADOR", nombre = "Ana Ruiz")
        val alumno = sembrarPersona("ALUMNO", nombre = "Alumno Colado")
        asignarEntrenador(grupo, zoe)
        asignarEntrenador(grupo, ana)
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_entrenador (grupo_id, club_id, entrenador_id) VALUES (?, ?, ?)",
            grupo.value,
            club.value,
            alumno.value,
        )

        entrenadores(grupo).map { it.id } shouldBe listOf(ana, zoe)
    }

    @Test
    fun `findCoaches no cruza la frontera de club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val grupo = crearGrupo("Propio")
        val entrenadorDeOtroClub = sembrarPersona("ENTRENADOR", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_entrenador (grupo_id, club_id, entrenador_id) VALUES (?, ?, ?)",
            grupo.value,
            club.value,
            entrenadorDeOtroClub.value,
        )

        entrenadores(grupo).shouldBeEmpty()
    }

    private fun entrenadores(groupId: GroupId): List<GroupCoach> = enTransaccion { groups.findCoaches(club, groupId) }

    private fun asignarEntrenador(
        groupId: GroupId,
        entrenador: PersonId,
    ) = enTransaccion { groups.assignCoach(club, groupId, entrenador) }

    private fun quitarEntrenador(
        groupId: GroupId,
        entrenador: PersonId,
    ): Int = enTransaccion { groups.unassignCoach(club, groupId, entrenador) }

    private fun contarAsignaciones(
        groupId: GroupId,
        entrenador: PersonId,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.grupo_entrenador WHERE grupo_id = ? AND entrenador_id = ?",
            Int::class.java,
            groupId.value,
            entrenador.value,
        ) ?: 0

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    /**
     * Siembra el grupo con SQL crudo, no con `groups.save`: ese método está `@AuthScope(Scope.CLUB)` y el aspecto
     * exige que el `clubId` coincida con el del principal autenticado, así que no sirve para sembrar el grupo "de
     * otro club" que necesitan los tests de frontera -- mismo motivo por el que `GroupRepositoryIntegrationTest`
     * siembra por SQL en vez de pasar por el repositorio.
     */
    private fun crearGrupo(
        nombre: String,
        club: ClubId = this.club,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)",
            id,
            club.value,
            nombre,
        )
        return GroupId.of(id)
    }

    private fun sembrarPersona(
        rol: String,
        club: ClubId = this.club,
        nombre: String? = null,
    ): PersonId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            nombre ?: "Persona $rol",
            "persona-$id@club.test",
            rol,
            UuidCreator.getTimeOrderedEpoch(),
        )
        return PersonId.of(id)
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
}
