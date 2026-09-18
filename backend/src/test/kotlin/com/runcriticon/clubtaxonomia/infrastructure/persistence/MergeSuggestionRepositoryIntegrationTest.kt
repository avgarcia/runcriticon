package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * `sugerencia_fusion_grupo` contra Postgres real (LAL-96): lo único que puede verificar el `ON CONFLICT ... DO
 * UPDATE` que preserva un descarte ya registrado (AC3) y el `WHERE descartada_en IS NULL` de [dismiss]/[listActive].
 */
class MergeSuggestionRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var suggestions: MergeSuggestionRepository

    @Autowired private lateinit var groups: GroupRepository

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
    fun `upsert crea una sugerencia que aparece en listActive`() {
        val (a, b) = crearPar()
        val now = Instant.parse("2026-08-25T10:00:00Z")

        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, now)) }

        val activas = enTransaccion { suggestions.listActive(club) }
        activas.single().type shouldBe MergeSuggestionType.DUPLICADO
        activas.single().calculatedAt shouldBe now
    }

    @Test
    fun `upsert repetido solo actualiza calculadoEn`() {
        val (a, b) = crearPar()
        enTransaccion {
            suggestions.upsert(
                club,
                MergeSuggestion.duplicateOf(a, b, Instant.parse("2026-08-25T10:00:00Z")),
            )
        }
        val despues = Instant.parse("2026-08-26T10:00:00Z")

        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, despues)) }

        val activas = enTransaccion { suggestions.listActive(club) }
        activas shouldBe activas.distinct()
        activas.single().calculatedAt shouldBe despues
    }

    @Test
    fun `dismiss marca la sugerencia y deja de aparecer en listActive`() {
        val (a, b) = crearPar()
        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, Instant.now())) }

        val descartada = enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) }

        descartada shouldBe true
        enTransaccion { suggestions.listActive(club) }.shouldBeEmpty()
    }

    @Test
    fun `dismiss sobre una clave inexistente devuelve false`() {
        val (a, b) = crearPar()

        enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) } shouldBe false
    }

    @Test
    fun `dismiss sobre una sugerencia ya descartada devuelve false, no la vuelve a marcar`() {
        val (a, b) = crearPar()
        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, Instant.now())) }
        enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) } shouldBe true

        enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) } shouldBe false
    }

    /**
     * El corazón de AC3: un recálculo posterior que confirma la misma sugerencia (mismo `upsert`) no debe
     * resucitar un descarte ya registrado -- si lo hiciera, la sugerencia volvería a aparecer justo el ciclo
     * siguiente a que el admin la descartara.
     */
    @Test
    fun `un upsert posterior al descarte no lo revive`() {
        val (a, b) = crearPar()
        enTransaccion {
            suggestions.upsert(
                club,
                MergeSuggestion.duplicateOf(a, b, Instant.parse("2026-08-25T10:00:00Z")),
            )
        }
        enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) }

        enTransaccion {
            suggestions.upsert(
                club,
                MergeSuggestion.duplicateOf(a, b, Instant.parse("2026-08-26T10:00:00Z")),
            )
        }

        enTransaccion { suggestions.listActive(club) }.shouldBeEmpty()
    }

    @Test
    fun `delete quita la sugerencia sin importar si estaba descartada`() {
        val (a, b) = crearPar()
        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, Instant.now())) }
        enTransaccion { suggestions.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) }

        enTransaccion { suggestions.delete(club, a, b, MergeSuggestionType.DUPLICADO) }

        // Un upsert posterior sobre una clave ya borrada crea una fila nueva sin descarte -- prueba indirecta de
        // que `delete` no dejó nada atrás.
        enTransaccion { suggestions.upsert(club, MergeSuggestion.duplicateOf(a, b, Instant.now())) }
        enTransaccion { suggestions.listActive(club) }.shouldNotBeEmpty()
    }

    @Test
    fun `una sugerencia MICRO lista el mismo grupo en ambos lados con su nombre`() {
        val grupo = crearGrupo("Grupo diminuto")

        enTransaccion { suggestions.upsert(club, MergeSuggestion.micro(grupo, Instant.now())) }

        val activa = enTransaccion { suggestions.listActive(club) }.single()
        activa.groupAId shouldBe grupo
        activa.groupBId shouldBe grupo
        activa.groupAName shouldBe activa.groupBName
    }

    private fun crearPar(): Pair<GroupId, GroupId> {
        val a = crearGrupo("Grupo A ${UuidCreator.getTimeOrderedEpoch()}")
        val b = crearGrupo("Grupo B ${UuidCreator.getTimeOrderedEpoch()}")
        return MergeSuggestion.canonicalPair(a, b)
    }

    private fun crearGrupo(nombre: String): GroupId {
        val group = Group.create(club, nombre).shouldBeRight()
        enTransaccion { groups.save(club, group) }
        return group.id
    }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!
}
