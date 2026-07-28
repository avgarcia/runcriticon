package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.AddTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.CreateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ListTaxonomyQuery
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagKeyCommand
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.TagKeyEntityRepository
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.TagValueEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.autorizacion.spring.AuthScopeViolationException
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * Ida y vuelta del agregado `Taxonomy` contra Postgres real (Testcontainers): el adaptador escribe y rehidrata, el
 * índice único parcial se comporta como el invariante de dominio, la metadata viaja a `jsonb`, un club no ve la
 * taxonomía de otro y `AuthScopeEnforcementAspect` falla cerrado si el `clubId` de la firma no es el del principal.
 *
 * Cada método de test corre sobre una instancia nueva de la clase (JUnit) y por tanto sobre un `clubId` propio: no
 * hace falta limpiar filas entre tests. `tag_key.club_id` no lleva FK a `identidad.club` —sería una FK cruzada entre
 * esquemas— así que cualquier UUID sirve como club.
 */
class TaxonomyPersistenceIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var createTagKey: CreateTagKeyCommand

    @Autowired private lateinit var renameTagKey: RenameTagKeyCommand

    @Autowired private lateinit var archiveTagKey: ArchiveTagKeyCommand

    @Autowired private lateinit var addTagValue: AddTagValueCommand

    @Autowired private lateinit var archiveTagValue: ArchiveTagValueCommand

    @Autowired private lateinit var listTaxonomy: ListTaxonomyQuery

    @Autowired private lateinit var taxonomyRepository: TaxonomyRepository

    @Autowired private lateinit var tagKeyEntityRepository: TagKeyEntityRepository

    @Autowired private lateinit var tagValueEntityRepository: TagValueEntityRepository

    @Autowired private lateinit var transactions: TransactionTemplate

    private val clubId: UUID = UUID.randomUUID()
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

    @BeforeEach
    fun autenticar() {
        // El aspecto de @AuthScope(CLUB) contrasta el clubId de la firma contra el principal del
        // SecurityContextHolder; estos tests invocan los casos de uso directamente, sin pasar por el login HTTP.
        autenticar(admin)
    }

    @AfterEach
    fun limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `guarda el agregado completo y lo rehidrata en orden de alta`() {
        val key = createTagKey.execute(admin, "Nivel").shouldBeRight()
        addTagValue.execute(admin, key.id.value, "Principiante").shouldBeRight()
        addTagValue.execute(admin, key.id.value, "Avanzado").shouldBeRight()

        val taxonomy = listTaxonomy.execute(admin).shouldBeRight()

        taxonomy.activeKeys().map { it.label.value } shouldBe listOf("Nivel")
        taxonomy.assignableValues().map { it.label.value } shouldBe listOf("Principiante", "Avanzado")
        taxonomy.findKey(key.id).shouldNotBeNull().clubId shouldBe ClubId.of(clubId)
    }

    @Test
    fun `renombrar actualiza el nombre y conserva creado_en`() {
        val key = createTagKey.execute(admin, "Nivel").shouldBeRight()
        val createdAt = tagKeyEntityRepository.findById(key.id.value).orElseThrow().createdAt

        renameTagKey.execute(admin, key.id.value, "Nivel de experiencia").shouldBeRight()

        val row = tagKeyEntityRepository.findById(key.id.value).orElseThrow()
        row.name shouldBe "Nivel de experiencia"
        row.createdAt shouldBe createdAt
    }

    @Test
    fun `el nombre duplicado lo corta el dominio antes de llegar al indice unico`() {
        createTagKey.execute(admin, "Nivel").shouldBeRight()

        createTagKey
            .execute(admin, "  níVEL ")
            .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "níVEL"))

        tagKeyEntityRepository.findAllByClubId(clubId).size shouldBe 1
    }

    @Test
    fun `archivar un eje conserva su fila y libera el nombre para un alta nueva`() {
        val first = createTagKey.execute(admin, "Nivel").shouldBeRight()
        archiveTagKey.execute(admin, first.id.value).shouldBeRight()

        val second = createTagKey.execute(admin, "Nivel").shouldBeRight()

        second.id shouldNotBe first.id
        tagKeyEntityRepository.findAllByClubId(clubId).size shouldBe 2
        tagKeyEntityRepository
            .findById(first.id.value)
            .orElseThrow()
            .archivedAt
            .shouldNotBeNull()
    }

    @Test
    fun `archivar un valor conserva su fila y libera el nombre dentro del eje`() {
        val key = createTagKey.execute(admin, "Distancia").shouldBeRight()
        val first = addTagValue.execute(admin, key.id.value, "5K").shouldBeRight()
        archiveTagValue.execute(admin, first.id.value).shouldBeRight()

        val second = addTagValue.execute(admin, key.id.value, "5K").shouldBeRight()

        second.id shouldNotBe first.id
        tagValueEntityRepository.findAllByClubId(clubId).size shouldBe 2
        listTaxonomy
            .execute(admin)
            .shouldBeRight()
            .assignableValues()
            .map { it.id } shouldBe listOf(second.id)
    }

    @Test
    fun `la metadata de carrera viaja a jsonb y vuelve como Race`() {
        val key = createTagKey.execute(admin, "Objetivo").shouldBeRight()
        val value = addTagValue.execute(admin, key.id.value, "Maratón de Valencia").shouldBeRight()
        val race = TagValueMetadata.Race(date = LocalDate.of(2026, 12, 6), distance = Distance.K42)

        // Todavía no hay caso de uso que asigne metadata (llega con el editor de taxonomía): se ejercita el puerto
        // directamente para cubrir la ida y vuelta TagValueMetadata ↔ columna jsonb.
        transactions.executeWithoutResult {
            val stored = taxonomyRepository.findByClub(ClubId.of(clubId))
            val updated = stored.changeValueMetadata(value.id, race).shouldBeRight().taxonomy
            taxonomyRepository.save(ClubId.of(clubId), updated)
        }

        listTaxonomy
            .execute(admin)
            .shouldBeRight()
            .findValue(value.id)
            .shouldNotBeNull()
            .metadata shouldBe race
    }

    @Test
    fun `un club no ve la taxonomia de otro`() {
        createTagKey.execute(admin, "Nivel").shouldBeRight()
        val otherAdmin = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ADMIN)
        autenticar(otherAdmin)

        listTaxonomy.execute(otherAdmin).shouldBeRight().keys shouldBe emptyList()
    }

    @Test
    fun `el aspecto rechaza leer con un clubId distinto al del principal`() {
        shouldThrow<AuthScopeViolationException> {
            taxonomyRepository.findByClub(ClubId.of(UUID.randomUUID()))
        }
    }

    @Test
    fun `el aspecto rechaza escribir con un clubId distinto al del principal`() {
        val otherClub = ClubId.of(UUID.randomUUID())

        shouldThrow<AuthScopeViolationException> {
            transactions.executeWithoutResult { taxonomyRepository.save(otherClub, Taxonomy.empty(otherClub)) }
        }
    }

    private fun autenticar(principal: Principal) {
        val authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }
}
