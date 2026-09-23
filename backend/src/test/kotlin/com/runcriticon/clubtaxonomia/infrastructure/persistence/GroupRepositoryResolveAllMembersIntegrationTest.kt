package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
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
 * `resolveAllMembers` (sugerencia de fusión de micro-grupos) contra Postgres real: aparte de
 * [GroupRepositoryIntegrationTest] -- que ya roza el
 * límite de tamaño de clase -- porque es el único método nuevo del repositorio para esta historia.
 */
class GroupRepositoryResolveAllMembersIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    @BeforeEach
    fun autenticar() {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `resolveAllMembers devuelve la membresia de todos los grupos del club en un solo mapa`() {
        val valor = sembrarValor("nivel", "medio")
        val alumno = sembrarPersona()
        asignarTag(alumno, valor)
        val conMiembro = crearGrupo("Con miembro", setOf(valor))
        val sinMiembro = crearGrupo("Sin miembro", emptySet())

        val todos = enTransaccion { groups.resolveAllMembers(club) }

        todos[conMiembro] shouldBe setOf(alumno)
        todos[sinMiembro] shouldBe emptySet()
    }

    @Test
    fun `resolveAllMembers respeta los overrides igual que resolveMembers`() {
        val incluido = sembrarPersona()
        val grupo = crearGrupo("Con excepcion", emptySet())
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_alumno_override (grupo_id, club_id, alumno_id, incluido) " +
                "VALUES (?, ?, ?, TRUE)",
            grupo.value,
            club.value,
            incluido.value,
        )

        enTransaccion { groups.resolveAllMembers(club) }[grupo] shouldBe setOf(incluido)
    }

    @Test
    fun `resolveAllMembers no cruza la frontera de club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val valorDeOtroClub = sembrarValor("nivel", "medio", club = otroClub)
        val alumnoDeOtroClub = sembrarPersona(club = otroClub)
        asignarTag(alumnoDeOtroClub, valorDeOtroClub, club = otroClub)
        crearGrupo("Grupo ajeno", setOf(valorDeOtroClub), club = otroClub)

        enTransaccion { groups.resolveAllMembers(club) }.values.forEach { it.shouldBeEmpty() }
    }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun crearGrupo(
        nombre: String,
        requiredTagValueIds: Set<TagValueId>,
        club: ClubId = this.club,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        requiredTagValueIds.forEach { valueId ->
            jdbc.update(
                "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
                id,
                club.value,
                valueId.value,
            )
        }
        return GroupId.of(id)
    }

    private fun asignarTag(
        alumno: PersonId,
        valueId: TagValueId,
        club: ClubId = this.club,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            club.value,
            alumno.value,
            valueId.value,
        )
    }

    private fun sembrarPersona(club: ClubId = this.club): PersonId {
        val id = UuidCreator.getTimeOrderedEpoch()
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
        return PersonId.of(id)
    }

    private fun sembrarValor(
        eje: String,
        valor: String,
        club: ClubId = this.club,
    ): TagValueId {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            "$eje-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            valueId,
            keyId,
            club.value,
            valor,
        )
        return TagValueId.of(valueId)
    }

    private companion object {
        const val SUFIJO_UNICO = 8
    }
}
