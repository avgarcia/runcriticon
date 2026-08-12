package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
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
 * El listado de entrenadores contra Postgres real: filtro por rol y por club viven en SQL, no en el dominio.
 */
class CoachDirectoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var directory: CoachDirectory

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
    fun `devuelve solo a los entrenadores del club, no a los alumnos`() {
        val carlos = sembrarPersona("ENTRENADOR", nombre = "Carlos Ruiz")
        sembrarPersona("ALUMNO", nombre = "Ana Ruiz")

        listar().map { it.id } shouldBe listOf(carlos)
    }

    @Test
    fun `sin entrenadores la lista sale vacia, no un error`() {
        listar().shouldBeEmpty()
    }

    @Test
    fun `grupos y totalAlumnos salen vacios hasta que exista la asignacion entrenador-grupo`() {
        sembrarPersona("ENTRENADOR")

        val resultado = listar().single()

        resultado.groups.shouldBeEmpty()
        resultado.totalStudents shouldBe 0
    }

    @Test
    fun `ordena por nombre`() {
        val zoe = sembrarPersona("ENTRENADOR", nombre = "Zoe Martín")
        val ana = sembrarPersona("ENTRENADOR", nombre = "Ana Ruiz")

        listar().map { it.id } shouldBe listOf(ana, zoe)
    }

    /**
     * Anti-IDOR: se llama siempre con el club del principal autenticado (`club`, no `otroClub`) -- pasar `otroClub`
     * como parámetro haría que `AuthScopeEnforcementAspect` lance `AuthScopeViolationException` antes de llegar al
     * SQL, lo que verificaría el aspecto en vez de la guardia `club_id` de este repositorio.
     */
    @Test
    fun `no devuelve entrenadores de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        sembrarPersona("ENTRENADOR", club = otroClub)
        val propio = sembrarPersona("ENTRENADOR")

        listar().map { it.id } shouldBe listOf(propio)
    }

    private fun listar(): List<CoachWorkload> = enTransaccion { directory.listByClub(club) }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

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
