package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.application.usecases.groups.InMemoryGroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import java.time.LocalDate
import java.util.UUID

/** Alta, renombrado y archivado de ejes de la taxonomía, ejercitados por un admin sobre un doble en memoria. */
class TagKeyUseCasesTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)

        lateinit var repository: InMemoryTaxonomyRepository
        lateinit var groupRepository: InMemoryGroupRepository

        beforeTest {
            repository = InMemoryTaxonomyRepository(Taxonomy.empty(clubId))
            groupRepository = InMemoryGroupRepository()
        }

        test("crea un eje y lo persiste con el literal tecleado") {
            val created =
                CreateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, "  Nivel  ").shouldBeRight()

            created.label.value shouldBe "Nivel"
            created.archivedAt shouldBe null
            repository.saveCount shouldBe 1
            repository.findByClub(clubId).activeKeys().map { it.label.value } shouldBe listOf("Nivel")
        }

        test("un nombre repetido ignorando mayúsculas y acentos devuelve DuplicateLabel y no guarda") {
            val useCase = CreateTagKeyCommand(repository, mockk(relaxed = true))
            useCase.execute(admin, "Nivel").shouldBeRight()

            useCase
                .execute(admin, "  níVEL ")
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "níVEL"))

            repository.saveCount shouldBe 1
        }

        test("un nombre en blanco devuelve InvalidInput y no guarda") {
            CreateTagKeyCommand(repository, mockk(relaxed = true))
                .execute(admin, "   ")
                .shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "blank"))

            repository.saveCount shouldBe 0
        }

        test("renombra un eje existente") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()

            val renamed =
                RenameTagKeyCommand(repository, mockk(relaxed = true))
                    .execute(admin, created.id.value, "Nivel de experiencia")
                    .shouldBeRight()

            renamed.id shouldBe created.id
            repository.findByClub(clubId).activeKeys().map { it.label.value } shouldBe listOf("Nivel de experiencia")
        }

        test("renombrar un eje inexistente devuelve TagKeyNotFound y no guarda") {
            RenameTagKeyCommand(repository, mockk(relaxed = true))
                .execute(admin, UUID.randomUUID(), "Nivel")
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)

            repository.saveCount shouldBe 0
        }

        test("archivar un eje lo saca de los activos sin borrarlo") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()

            val archived =
                ArchiveTagKeyCommand(
                    repository,
                    groupRepository,
                    mockk(relaxed = true),
                ).execute(admin, created.id.value).shouldBeRight()

            archived.archivedAt.shouldNotBeNull()
            val stored = repository.findByClub(clubId)
            stored.activeKeys() shouldBe emptyList()
            stored.findKey(created.id).shouldNotBeNull()
        }

        test("archivar dos veces es idempotente: conserva el instante original") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()
            val useCase = ArchiveTagKeyCommand(repository, groupRepository, mockk(relaxed = true))
            val first = useCase.execute(admin, created.id.value).shouldBeRight()

            val second = useCase.execute(admin, created.id.value).shouldBeRight()

            second.archivedAt shouldBe first.archivedAt
        }

        test("archivar un eje libera su nombre para reutilizarlo") {
            val create = CreateTagKeyCommand(repository, mockk(relaxed = true))
            val created = create.execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(
                repository,
                groupRepository,
                mockk(relaxed = true),
            ).execute(admin, created.id.value).shouldBeRight()

            val reused = create.execute(admin, "Nivel").shouldBeRight()

            reused.id shouldNotBe created.id
        }

        test("archivar un eje requerido por un grupo vivo devuelve TagKeyRequiredByGroup y no lo archiva") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()
            val value =
                AddTagValueCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, created.id.value, "Principiante").shouldBeRight()
            val group = Group.create(clubId, "Grupo iniciación", setOf(value.id)).shouldBeRight()
            groupRepository =
                InMemoryGroupRepository(existing = mapOf(group.id to GroupDetail(group, emptyList(), emptyList())))

            ArchiveTagKeyCommand(repository, groupRepository, mockk(relaxed = true))
                .execute(admin, created.id.value)
                .shouldBeLeft(ClubTaxonomiaError.TagKeyRequiredByGroup(setOf(group.id)))

            repository
                .findByClub(clubId)
                .findKey(created.id)
                .shouldNotBeNull()
                .archivedAt shouldBe null
        }

        test("reactivar un eje archivado lo devuelve a los activos") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(
                repository,
                groupRepository,
                mockk(relaxed = true),
            ).execute(admin, created.id.value).shouldBeRight()

            val reactivated =
                ReactivateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, created.id.value).shouldBeRight()

            reactivated.archivedAt shouldBe null
            repository.findByClub(clubId).activeKeys().map { it.id } shouldBe listOf(created.id)
        }

        test("reactivar un eje ya activo es idempotente") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()

            ReactivateTagKeyCommand(
                repository,
                mockk(relaxed = true),
            ).execute(admin, created.id.value).shouldBeRight().archivedAt shouldBe
                null
        }

        test("reactivar un eje inexistente devuelve TagKeyNotFound") {
            ReactivateTagKeyCommand(repository, mockk(relaxed = true))
                .execute(admin, UUID.randomUUID())
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)
        }

        test("reactivar choca con DuplicateLabel si el nombre se reocupó mientras estaba archivado") {
            val create = CreateTagKeyCommand(repository, mockk(relaxed = true))
            val original = create.execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(
                repository,
                groupRepository,
                mockk(relaxed = true),
            ).execute(admin, original.id.value).shouldBeRight()
            create.execute(admin, "Nivel").shouldBeRight()

            ReactivateTagKeyCommand(repository, mockk(relaxed = true))
                .execute(admin, original.id.value)
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel"))
        }

        // --- tipo del eje (LAL-84) ---------------------------------------------------------------------------------

        test("crea un eje con el tipo pedido") {
            val created =
                CreateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, "Objetivo", TagKeyType.RACE).shouldBeRight()

            created.type shouldBe TagKeyType.RACE
        }

        test("crea un eje sin tipo explícito como SIMPLE") {
            val created = CreateTagKeyCommand(repository, mockk(relaxed = true)).execute(admin, "Nivel").shouldBeRight()

            created.type shouldBe TagKeyType.SIMPLE
        }

        test("cambia el tipo de un eje") {
            val created =
                CreateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, "Objetivo").shouldBeRight()

            val changed =
                ChangeTagKeyTypeCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, created.id.value, TagKeyType.RACE).shouldBeRight()

            changed.type shouldBe TagKeyType.RACE
        }

        test("cambiar el tipo de un eje inexistente devuelve TagKeyNotFound y no guarda") {
            ChangeTagKeyTypeCommand(repository, mockk(relaxed = true))
                .execute(admin, UUID.randomUUID(), TagKeyType.RACE)
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)

            repository.saveCount shouldBe 0
        }

        test("degradar a SIMPLE se rechaza si el eje tiene un valor con metadata de carrera") {
            val created =
                CreateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, "Objetivo", TagKeyType.RACE).shouldBeRight()
            AddTagValueCommand(repository, mockk(relaxed = true))
                .execute(
                    admin,
                    created.id.value,
                    "Maratón",
                    RaceMetadataInput(date = LocalDate.of(2026, 12, 6), distance = "42K"),
                ).shouldBeRight()

            ChangeTagKeyTypeCommand(repository, mockk(relaxed = true))
                .execute(admin, created.id.value, TagKeyType.SIMPLE)
                .shouldBeLeft(ClubTaxonomiaError.Conflict("tag_key_has_race_values"))
        }

        test("degradar a SIMPLE se permite si ningún valor tiene metadata de carrera") {
            val created =
                CreateTagKeyCommand(
                    repository,
                    mockk(relaxed = true),
                ).execute(admin, "Objetivo", TagKeyType.RACE).shouldBeRight()
            AddTagValueCommand(
                repository,
                mockk(relaxed = true),
            ).execute(admin, created.id.value, "sin carrera").shouldBeRight()

            val changed =
                ChangeTagKeyTypeCommand(repository, mockk(relaxed = true))
                    .execute(admin, created.id.value, TagKeyType.SIMPLE)
                    .shouldBeRight()

            changed.type shouldBe TagKeyType.SIMPLE
        }
    })
