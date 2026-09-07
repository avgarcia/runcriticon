package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.application.usecases.groups.InMemoryGroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

/** Alta, renombrado y archivado de valores de un eje, ejercitados por un admin sobre un doble en memoria. */
class TagValueUseCasesTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)

        lateinit var repository: InMemoryTaxonomyRepository
        lateinit var groupRepository: InMemoryGroupRepository
        lateinit var keyId: UUID

        beforeTest {
            repository = InMemoryTaxonomyRepository(Taxonomy.empty(clubId))
            groupRepository = InMemoryGroupRepository()
            keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Distancia")
                    .shouldBeRight()
                    .id.value
        }

        test("añade un valor al eje con metadata vacía") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, " 5K ").shouldBeRight()

            created.label.value shouldBe "5K"
            created.metadata shouldBe TagValueMetadata.Empty
            repository.findByClub(clubId).assignableValues().map { it.label.value } shouldBe listOf("5K")
        }

        test("un valor repetido dentro del mismo eje devuelve DuplicateLabel") {
            val useCase = AddTagValueCommand(repository)
            useCase.execute(admin, keyId, "5K").shouldBeRight()

            useCase.execute(admin, keyId, " 5k ").shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("valor", "5k"))
        }

        test("el mismo valor en dos ejes distintos no choca") {
            val otherKeyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Objetivo")
                    .shouldBeRight()
                    .id.value
            val useCase = AddTagValueCommand(repository)
            useCase.execute(admin, keyId, "5K").shouldBeRight()

            useCase.execute(admin, otherKeyId, "5K").shouldBeRight()
        }

        test("añadir a un eje archivado devuelve Conflict") {
            ArchiveTagKeyCommand(repository, groupRepository).execute(admin, keyId).shouldBeRight()

            AddTagValueCommand(repository)
                .execute(admin, keyId, "5K")
                .shouldBeLeft(ClubTaxonomiaError.Conflict("tag_key_archived"))
        }

        test("añadir a un eje inexistente devuelve TagKeyNotFound") {
            AddTagValueCommand(repository)
                .execute(admin, UUID.randomUUID(), "5K")
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)
        }

        test("renombra un valor existente") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, "5K").shouldBeRight()

            val renamed =
                RenameTagValueCommand(repository).execute(admin, created.id.value, "5 km").shouldBeRight()

            renamed.id shouldBe created.id
            repository.findByClub(clubId).assignableValues().map { it.label.value } shouldBe listOf("5 km")
        }

        test("renombrar un valor inexistente devuelve TagValueNotFound") {
            RenameTagValueCommand(repository)
                .execute(admin, UUID.randomUUID(), "5K")
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
        }

        test("archivar un valor lo saca de los asignables y libera su nombre") {
            val add = AddTagValueCommand(repository)
            val created = add.execute(admin, keyId, "5K").shouldBeRight()

            val archived =
                ArchiveTagValueCommand(repository, groupRepository).execute(admin, created.id.value).shouldBeRight()

            archived.archivedAt.shouldNotBeNull()
            repository.findByClub(clubId).assignableValues() shouldBe emptyList()
            add.execute(admin, keyId, "5K").shouldBeRight()
        }

        test("archivar un valor requerido por un grupo vivo devuelve TagValueRequiredByGroup y no lo archiva") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, "5K").shouldBeRight()
            val group = Group.create(clubId, "Grupo 5K", setOf(created.id)).shouldBeRight()
            groupRepository =
                InMemoryGroupRepository(existing = mapOf(group.id to GroupDetail(group, emptyList(), emptyList())))

            ArchiveTagValueCommand(repository, groupRepository)
                .execute(admin, created.id.value)
                .shouldBeLeft(ClubTaxonomiaError.TagValueRequiredByGroup(setOf(group.id)))

            repository
                .findByClub(clubId)
                .findValue(created.id)
                .shouldNotBeNull()
                .archivedAt shouldBe null
        }

        test("archivar dos veces es idempotente: conserva el instante original") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, "5K").shouldBeRight()
            val useCase = ArchiveTagValueCommand(repository, groupRepository)
            val first = useCase.execute(admin, created.id.value).shouldBeRight()

            useCase.execute(admin, created.id.value).shouldBeRight().archivedAt shouldBe first.archivedAt
        }

        test("reactivar un valor archivado lo devuelve a los asignables") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, "5K").shouldBeRight()
            ArchiveTagValueCommand(repository, groupRepository).execute(admin, created.id.value).shouldBeRight()

            val reactivated = ReactivateTagValueCommand(repository).execute(admin, created.id.value).shouldBeRight()

            reactivated.archivedAt shouldBe null
            repository.findByClub(clubId).assignableValues().map { it.id } shouldBe listOf(created.id)
        }

        test("reactivar un valor se permite aunque su eje siga archivado, pero no lo hace asignable") {
            val created = AddTagValueCommand(repository).execute(admin, keyId, "5K").shouldBeRight()
            ArchiveTagValueCommand(repository, groupRepository).execute(admin, created.id.value).shouldBeRight()
            ArchiveTagKeyCommand(repository, groupRepository).execute(admin, keyId).shouldBeRight()

            ReactivateTagValueCommand(repository).execute(admin, created.id.value).shouldBeRight()

            repository.findByClub(clubId).assignableValues() shouldBe emptyList()
            repository
                .findByClub(clubId)
                .findValue(created.id)
                .shouldNotBeNull()
                .archivedAt shouldBe null
        }

        test("reactivar un valor inexistente devuelve TagValueNotFound") {
            ReactivateTagValueCommand(repository)
                .execute(admin, UUID.randomUUID())
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
        }

        test("reactivar choca con DuplicateLabel si el literal se reocupó dentro del eje") {
            val add = AddTagValueCommand(repository)
            val original = add.execute(admin, keyId, "5K").shouldBeRight()
            ArchiveTagValueCommand(repository, groupRepository).execute(admin, original.id.value).shouldBeRight()
            add.execute(admin, keyId, "5K").shouldBeRight()

            ReactivateTagValueCommand(repository)
                .execute(admin, original.id.value)
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("valor", "5K"))
        }
    })
