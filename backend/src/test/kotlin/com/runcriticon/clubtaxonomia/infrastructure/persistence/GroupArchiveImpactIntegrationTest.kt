package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
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
import kotlin.properties.Delegates

/**
 * `GroupRepository.findGroupsRequiringAnyTagValue` (LAL-83) contra Postgres real: variante con nombre y con el caso
 * borde de ADR-0002 D3 de `findGroupIdsByAnyRequiredTagValue`, ya cubierta en `GroupRepositoryIntegrationTest`.
 * Fichero aparte (no un método más ahí) porque esa clase ya está en el límite de tamaño que exige detekt.
 */
class GroupArchiveImpactIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    private var nivelMedio: TagValueId by Delegates.notNull()
    private var objetivoMaraton: TagValueId by Delegates.notNull()

    @BeforeEach
    fun prepara() {
        nivelMedio = sembrarValor("nivel", "medio")
        objetivoMaraton = sembrarValor("objetivo", "maraton")
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `trae el nombre del grupo afectado`() {
        val grupo = crearGrupo("Con nivel", setOf(nivelMedio))

        val afectados = enTransaccion { groups.findGroupsRequiringAnyTagValue(club, setOf(nivelMedio)) }

        val afectado = afectados.single()
        afectado.groupId shouldBe grupo
        afectado.groupName.value shouldBe "Con nivel"
    }

    @Test
    fun `marca wouldLoseAllRequiredTags cuando todos sus tags se archivan`() {
        crearGrupo("Solo un tag", setOf(nivelMedio))

        val afectados = enTransaccion { groups.findGroupsRequiringAnyTagValue(club, setOf(nivelMedio)) }

        afectados.single().wouldLoseAllRequiredTags shouldBe true
    }

    @Test
    fun `no marca wouldLoseAllRequiredTags si conserva otro tag requerido`() {
        crearGrupo("Con dos tags", setOf(nivelMedio, objetivoMaraton))

        val afectados = enTransaccion { groups.findGroupsRequiringAnyTagValue(club, setOf(nivelMedio)) }

        afectados.single().wouldLoseAllRequiredTags shouldBe false
    }

    @Test
    fun `ignora grupos cuyo filtro no toca el valor`() {
        crearGrupo("Con objetivo", setOf(objetivoMaraton))
        crearGrupo("Sin filtro", emptySet())

        enTransaccion { groups.findGroupsRequiringAnyTagValue(club, setOf(nivelMedio)) }.shouldBeEmpty()
    }

    @Test
    fun `no cruza la frontera de club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val valorDeOtroClub = sembrarValor("nivel", "medio", club = otroClub)
        crearGrupo("Grupo de otro club", setOf(valorDeOtroClub), club = otroClub)

        enTransaccion { groups.findGroupsRequiringAnyTagValue(club, setOf(valorDeOtroClub)) }.shouldBeEmpty()
    }

    @Test
    fun `con conjunto vacio devuelve vacio`() {
        crearGrupo("Con nivel", setOf(nivelMedio))

        enTransaccion { groups.findGroupsRequiringAnyTagValue(club, emptySet()) }.shouldBeEmpty()
    }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun crearGrupo(
        nombre: String,
        requiredTagValueIds: Set<TagValueId>,
        club: ClubId = this.club,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)",
            id,
            club.value,
            nombre,
        )
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

    /** Cuelga un valor nuevo de un eje nuevo: los tests no dependen así de la taxonomía sembrada por migración. */
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
            // Sufijo corto por el límite de 40 caracteres de la columna, tomado del final del UUID: los v7
            // comparten el prefijo temporal y colisionarían entre tests del mismo segundo.
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
