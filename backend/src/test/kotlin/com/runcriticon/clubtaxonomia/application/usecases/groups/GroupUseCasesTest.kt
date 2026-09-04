package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
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
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento de los casos de uso de grupo con la base sustituida por dobles: qué filtro se acepta, qué se
 * guarda, qué ajuste manual se escribe y qué se rechaza antes de tocar nada.
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
            // `publishEvent` relajado y sin capturar: estos tests de creación no comprueban el evento publicado,
            // solo que `create` no falle por tener la dependencia -- eso lo cubren los tests de la sección de
            // ajuste manual, donde sí importa el contenido.
            create =
                CreateGroupCommand(
                    taxonomy,
                    groups,
                    GroupMembershipPublisher(groups, mockk(relaxed = true), mockk(relaxed = true)),
                )
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

            val listado = ListGroupsQuery(groups, mockk(relaxed = true)).execute(admin).shouldBeRight()

            listado
                .single()
                .group.name.value shouldBe "Maratón Valencia avanzado"
            listado.single().memberCount shouldBe 12
        }

        test("listar un club sin grupos devuelve lista vacia, no error") {
            ListGroupsQuery(groups, mockk(relaxed = true)).execute(admin).shouldBeRight() shouldHaveSize 0
        }

        test("previsualizar valida el filtro antes de consultar") {
            preview
                .execute(admin, listOf(UUID.randomUUID()))
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
            preview.execute(admin, listOf(montanaArchivada.id.value)).shouldBeLeft()

            groups.previewCount shouldBe 0
        }

        context("detalle y ajuste manual de pertenencia") {
            val grupo = Group.create(club, "Maratón Valencia avanzado", setOf(medio.id)).shouldBeRight()
            val alumno = PersonId.of(UuidCreator.getTimeOrderedEpoch())
            val detalle = GroupDetail(grupo, members = emptyList(), exclusions = emptyList())

            lateinit var conGrupo: InMemoryGroupRepository
            lateinit var detail: GetGroupDetailQuery
            lateinit var ajustar: OverrideGroupMembershipCommand
            lateinit var quitar: ClearGroupMembershipOverrideCommand
            lateinit var eventPublisher: ApplicationEventPublisher
            lateinit var published: MutableList<Any>
            lateinit var membershipPublisher: GroupMembershipPublisher

            beforeEach {
                conGrupo = InMemoryGroupRepository(existing = mapOf(grupo.id to detalle))
                detail = GetGroupDetailQuery(conGrupo)
                published = mutableListOf()
                eventPublisher = mockk(relaxed = true)
                every { eventPublisher.publishEvent(capture(published)) } returns Unit
                membershipPublisher = GroupMembershipPublisher(conGrupo, eventPublisher, mockk(relaxed = true))
                ajustar = OverrideGroupMembershipCommand(conGrupo, AlwaysStudent, membershipPublisher)
                quitar = ClearGroupMembershipOverrideCommand(conGrupo, membershipPublisher)
            }

            test("consultar el detalle devuelve lo que resuelve el repositorio, con el club del actor") {
                detail.execute(admin, grupo.id.value).shouldBeRight() shouldBe detalle

                conGrupo.detailCalls.single().first shouldBe club
            }

            // El repositorio devuelve null tanto si el grupo no existe como si es de otro club: aquí colapsan en el
            // mismo error, para no dejar enumerar grupos ajenos.
            test("consultar un grupo inexistente o de otro club da GroupNotFound") {
                detail
                    .execute(admin, UUID.randomUUID())
                    .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)
            }

            test("ajustar la pertenencia escribe la excepcion, devuelve el detalle recalculado y publica el snapshot") {
                ajustar.execute(admin, grupo.id.value, alumno.value, included = true).shouldBeRight() shouldBe detalle

                conGrupo.overrides[grupo.id to alumno] shouldBe true
                conGrupo.overrideCalls.single().first shouldBe club

                val evento = published.single().shouldBeInstanceOf<MembresiaDeGrupoCambiada>()
                evento.aggregateId shouldBe grupo.id.value
                evento.clubId shouldBe club.value
                evento.actorId shouldBe admin.userId
            }

            test("ajustar con included=false tambien publica el snapshot de membresia") {
                ajustar.execute(admin, grupo.id.value, alumno.value, included = false).shouldBeRight()

                val evento = published.single().shouldBeInstanceOf<MembresiaDeGrupoCambiada>()
                evento.aggregateId shouldBe grupo.id.value
            }

            test("ajustar con el sentido contrario sobrescribe la excepcion y publica un snapshot cada vez") {
                ajustar.execute(admin, grupo.id.value, alumno.value, included = true).shouldBeRight()
                ajustar.execute(admin, grupo.id.value, alumno.value, included = false).shouldBeRight()

                conGrupo.overrides.size shouldBe 1
                conGrupo.overrides[grupo.id to alumno] shouldBe false
                published shouldHaveSize 2
                published.forEach { it.shouldBeInstanceOf<MembresiaDeGrupoCambiada>() }
            }

            test("ajustar la pertenencia en un grupo inexistente no escribe nada ni publica") {
                ajustar
                    .execute(admin, UUID.randomUUID(), alumno.value, included = true)
                    .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)

                conGrupo.overrideCount shouldBe 0
                published shouldHaveSize 0
            }

            // Cubre de una vez los tres modos que el puerto colapsa: no existe, es entrenador o es de otro club. Sin
            // esta guarda quedaría una excepción invisible, porque el detalle solo devuelve alumnos del club.
            test("ajustar la pertenencia de quien no es alumno del club no escribe nada") {
                val sinAlumno = OverrideGroupMembershipCommand(conGrupo, NeverStudent, membershipPublisher)

                sinAlumno
                    .execute(admin, grupo.id.value, alumno.value, included = true)
                    .shouldBeLeft(ClubTaxonomiaError.StudentNotFound)

                conGrupo.overrideCount shouldBe 0
            }

            test("el grupo se comprueba antes que el alumno") {
                val sinAlumno = OverrideGroupMembershipCommand(conGrupo, NeverStudent, membershipPublisher)

                sinAlumno
                    .execute(admin, UUID.randomUUID(), alumno.value, included = true)
                    .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)
            }

            test("quitar el ajuste borra la excepcion y publica el snapshot resultante") {
                ajustar.execute(admin, grupo.id.value, alumno.value, included = true).shouldBeRight()
                published.clear()

                quitar.execute(admin, grupo.id.value, alumno.value).shouldBeRight()

                conGrupo.overrides.size shouldBe 0
                val evento = published.single().shouldBeInstanceOf<MembresiaDeGrupoCambiada>()
                evento.aggregateId shouldBe grupo.id.value
            }

            // Idempotente: quitar lo que no está deja el mismo estado, y el llamante no tiene por qué enterarse. Con
            // el snapshot completo publica igual: LAL-94 no lo hacía, porque entonces dependía de saber si el
            // alumno quedaba dentro o fuera; ahora se resuelve la membresía tal cual queda, sea cual sea.
            test("quitar un ajuste que no existia no es error y tambien publica") {
                quitar.execute(admin, grupo.id.value, alumno.value).shouldBeRight()

                conGrupo.deleteCalls shouldHaveSize 1
                published shouldHaveSize 1
            }

            test("quitar el ajuste en un grupo inexistente da GroupNotFound sin borrar ni publicar") {
                quitar
                    .execute(admin, UUID.randomUUID(), alumno.value)
                    .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)

                conGrupo.deleteCalls shouldHaveSize 0
                published shouldHaveSize 0
            }
        }
    })

/** El caso normal: la persona existe en el club y es alumno. */
private object AlwaysStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true
}

/** Cubre de una vez los tres modos de fallo que el puerto colapsa: no existe, es entrenador, o es de otro club. */
private object NeverStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = false
}

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
