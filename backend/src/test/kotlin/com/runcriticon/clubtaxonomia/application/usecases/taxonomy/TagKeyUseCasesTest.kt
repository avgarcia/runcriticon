package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
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
import java.util.UUID

/** Alta, renombrado y archivado de ejes de la taxonomía, ejercitados por un admin sobre un doble en memoria. */
class TagKeyUseCasesTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)

        lateinit var repository: InMemoryTaxonomyRepository

        beforeTest { repository = InMemoryTaxonomyRepository(Taxonomy.empty(clubId)) }

        test("crea un eje y lo persiste con el literal tecleado") {
            val created = CreateTagKeyCommand(repository).execute(admin, "  Nivel  ").shouldBeRight()

            created.label.value shouldBe "Nivel"
            created.archivedAt shouldBe null
            repository.saveCount shouldBe 1
            repository.findByClub(clubId).activeKeys().map { it.label.value } shouldBe listOf("Nivel")
        }

        test("un nombre repetido ignorando mayúsculas y acentos devuelve DuplicateLabel y no guarda") {
            val useCase = CreateTagKeyCommand(repository)
            useCase.execute(admin, "Nivel").shouldBeRight()

            useCase
                .execute(admin, "  níVEL ")
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "níVEL"))

            repository.saveCount shouldBe 1
        }

        test("un nombre en blanco devuelve InvalidInput y no guarda") {
            CreateTagKeyCommand(repository)
                .execute(admin, "   ")
                .shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "blank"))

            repository.saveCount shouldBe 0
        }

        test("renombra un eje existente") {
            val created = CreateTagKeyCommand(repository).execute(admin, "Nivel").shouldBeRight()

            val renamed =
                RenameTagKeyCommand(repository)
                    .execute(admin, created.id.value, "Nivel de experiencia")
                    .shouldBeRight()

            renamed.id shouldBe created.id
            repository.findByClub(clubId).activeKeys().map { it.label.value } shouldBe listOf("Nivel de experiencia")
        }

        test("renombrar un eje inexistente devuelve TagKeyNotFound y no guarda") {
            RenameTagKeyCommand(repository)
                .execute(admin, UUID.randomUUID(), "Nivel")
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)

            repository.saveCount shouldBe 0
        }

        test("archivar un eje lo saca de los activos sin borrarlo") {
            val created = CreateTagKeyCommand(repository).execute(admin, "Nivel").shouldBeRight()

            val archived = ArchiveTagKeyCommand(repository).execute(admin, created.id.value).shouldBeRight()

            archived.archivedAt.shouldNotBeNull()
            val stored = repository.findByClub(clubId)
            stored.activeKeys() shouldBe emptyList()
            stored.findKey(created.id).shouldNotBeNull()
        }

        test("archivar dos veces es idempotente: conserva el instante original") {
            val created = CreateTagKeyCommand(repository).execute(admin, "Nivel").shouldBeRight()
            val useCase = ArchiveTagKeyCommand(repository)
            val first = useCase.execute(admin, created.id.value).shouldBeRight()

            val second = useCase.execute(admin, created.id.value).shouldBeRight()

            second.archivedAt shouldBe first.archivedAt
        }

        test("archivar un eje libera su nombre para reutilizarlo") {
            val create = CreateTagKeyCommand(repository)
            val created = create.execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(repository).execute(admin, created.id.value).shouldBeRight()

            val reused = create.execute(admin, "Nivel").shouldBeRight()

            reused.id shouldNotBe created.id
        }

        test("reactivar un eje archivado lo devuelve a los activos") {
            val created = CreateTagKeyCommand(repository).execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(repository).execute(admin, created.id.value).shouldBeRight()

            val reactivated = ReactivateTagKeyCommand(repository).execute(admin, created.id.value).shouldBeRight()

            reactivated.archivedAt shouldBe null
            repository.findByClub(clubId).activeKeys().map { it.id } shouldBe listOf(created.id)
        }

        test("reactivar un eje ya activo es idempotente") {
            val created = CreateTagKeyCommand(repository).execute(admin, "Nivel").shouldBeRight()

            ReactivateTagKeyCommand(repository).execute(admin, created.id.value).shouldBeRight().archivedAt shouldBe
                null
        }

        test("reactivar un eje inexistente devuelve TagKeyNotFound") {
            ReactivateTagKeyCommand(repository)
                .execute(admin, UUID.randomUUID())
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)
        }

        test("reactivar choca con DuplicateLabel si el nombre se reocupó mientras estaba archivado") {
            val create = CreateTagKeyCommand(repository)
            val original = create.execute(admin, "Nivel").shouldBeRight()
            ArchiveTagKeyCommand(repository).execute(admin, original.id.value).shouldBeRight()
            create.execute(admin, "Nivel").shouldBeRight()

            ReactivateTagKeyCommand(repository)
                .execute(admin, original.id.value)
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel"))
        }
    })
