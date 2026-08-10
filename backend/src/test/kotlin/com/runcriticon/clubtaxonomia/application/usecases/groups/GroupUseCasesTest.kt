package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento de los dos casos de uso de grupo con la base sustituida por dobles: qué filtro se acepta, qué se
 * guarda y qué se rechaza antes de tocar nada.
 */
class GroupUseCasesTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val medio = valor("medio")
        val maraton = valor("maratón valencia")
        val montanaArchivada = valor("montaña", archived = true)
        val trail = valor("trail")
        val nivel = clave(club, "nivel", listOf(medio))
        val objetivo = clave(club, "objetivo", listOf(maraton))
        val terreno = clave(club, "terreno", listOf(montanaArchivada))
        val ejeArchivado = clave(club, "superficie", listOf(trail), archived = true)

        val previsualizacion =
            GroupMembers(listOf(GroupMember(PersonId.of(UuidCreator.getTimeOrderedEpoch()), "Pedro Cordero")))

        lateinit var groups: InMemoryGroupRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository
        lateinit var create: CreateGroupCommand
        lateinit var preview: PreviewGroupMembersQuery

        beforeEach {
            groups = InMemoryGroupRepository(previsualizacion)
            taxonomy =
                InMemoryTaxonomyRepository(Taxonomy.rehydrate(club, listOf(nivel, objetivo, terreno, ejeArchivado)))
            create = CreateGroupCommand(taxonomy, groups)
            preview = PreviewGroupMembersQuery(taxonomy, groups)
        }

        test("crear un grupo lo guarda con su nombre y su filtro") {
            val group =
                create
                    .execute(admin, "  Maratón Valencia avanzado  ", listOf(medio.id.value, maraton.id.value))
                    .shouldBeRight()

            group.name.value shouldBe "Maratón Valencia avanzado"
            group.requiredTagValueIds shouldBe setOf(medio.id, maraton.id)
            groups.saved.single().second shouldBe group
        }

        test("un filtro vacio crea un grupo sin tags requeridos") {
            val group = create.execute(admin, "Solo a mano", emptyList()).shouldBeRight()

            group.requiredTagValueIds shouldHaveSize 0
            groups.saveCount shouldBe 1
        }

        test("los ids repetidos se colapsan") {
            val group =
                create.execute(admin, "Nivel medio", listOf(medio.id.value, medio.id.value)).shouldBeRight()

            group.requiredTagValueIds shouldHaveSize 1
        }

        test("un nombre en blanco se rechaza y no guarda") {
            val error = create.execute(admin, "   ", listOf(medio.id.value)).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.InvalidInput>().field shouldBe "nombre"
            groups.saveCount shouldBe 0
        }

        test("un nombre demasiado largo se rechaza") {
            val error = create.execute(admin, "x".repeat(81), emptyList()).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.InvalidInput>().reason shouldBe "too_long"
        }

        // Con las dos cosas mal a la vez gana el nombre: es lo que quien está delante puede arreglar sin salir del
        // formulario.
        test("con el nombre en blanco y un tag inexistente gana el error del nombre") {
            val error = create.execute(admin, "", listOf(UUID.randomUUID())).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.InvalidInput>().reason shouldBe "blank"
        }

        // Un valor de otro club no está en la taxonomía del club del actor, así que colapsa en el mismo 404 que uno
        // inexistente: la respuesta no delata que ese id existe en otra parte.
        test("un tag desconocido o de otro club se rechaza sin guardar") {
            create
                .execute(admin, "Fantasma", listOf(UUID.randomUUID()))
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)

            groups.saveCount shouldBe 0
        }

        test("un tag archivado se rechaza sin guardar") {
            val error = create.execute(admin, "Montañeros", listOf(montanaArchivada.id.value)).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.Conflict>().reason shouldBe "tag_value_not_assignable"
            groups.saveCount shouldBe 0
        }

        test("un valor activo de un eje archivado tampoco es asignable") {
            val error = create.execute(admin, "Trail", listOf(trail.id.value)).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.Conflict>().reason shouldBe "tag_value_not_assignable"
        }

        test("previsualizar devuelve los alumnos que resuelve el repositorio") {
            val members = preview.execute(admin, listOf(medio.id.value)).shouldBeRight()

            members shouldBe previsualizacion
            members.total shouldBe 1
            groups.previewCalls.single().second shouldBe setOf(medio.id)
        }

        test("previsualizar con un filtro vacio no consulta miembros y devuelve cero") {
            groups = InMemoryGroupRepository()
            preview = PreviewGroupMembersQuery(taxonomy, groups)

            preview.execute(admin, emptyList()).shouldBeRight().total shouldBe 0
        }

        test("listar devuelve los grupos que resuelve el repositorio, con su recuento") {
            val group = Group.create(club, "Maratón Valencia avanzado", setOf(medio.id)).shouldBeRight()
            groups = InMemoryGroupRepository(summaries = listOf(GroupSummary(group, memberCount = 12)))

            val listado = ListGroupsQuery(groups).execute(admin).shouldBeRight()

            listado
                .single()
                .group.name.value shouldBe "Maratón Valencia avanzado"
            listado.single().memberCount shouldBe 12
        }

        test("listar un club sin grupos devuelve lista vacia, no error") {
            ListGroupsQuery(groups).execute(admin).shouldBeRight() shouldHaveSize 0
        }

        test("previsualizar valida el filtro antes de consultar") {
            preview
                .execute(admin, listOf(UUID.randomUUID()))
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
            preview.execute(admin, listOf(montanaArchivada.id.value)).shouldBeLeft()

            groups.previewCount shouldBe 0
        }
    })

private fun valor(
    label: String,
    archived: Boolean = false,
) = TagValue(
    id = TagValueId.new(),
    label = TagLabel.forValue(label).getOrNull()!!,
    metadata = TagValueMetadata.Empty,
    archivedAt = if (archived) Instant.parse("2026-07-01T10:00:00Z") else null,
)

private fun clave(
    club: ClubId,
    label: String,
    values: List<TagValue>,
    archived: Boolean = false,
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    archivedAt = if (archived) Instant.parse("2026-07-01T10:00:00Z") else null,
    values = values,
)
