package com.runcriticon.clubtaxonomia.application.usecases

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.groups.GetGroupDetailQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.application.usecases.groups.InMemoryGroupRepository
import com.runcriticon.clubtaxonomia.application.usecases.groups.PreviewGroupMembersQuery
import com.runcriticon.clubtaxonomia.application.usecases.students.InMemoryStudentDirectory
import com.runcriticon.clubtaxonomia.application.usecases.students.ListStudentsQuery
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.InMemoryHealthConsentReader
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.InMemoryStudentTagRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ListStudentTagsQuery
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupExclusion
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMemberOrigin
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupMembership
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.ReservedRole
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

/**
 * Qué alumnos registra `@AuditAccess(SALUD)` en cada lectura que puede exponer el tag `estado = lesión`
 * (ADR-0009 D15): solo los que quedan expuestos como lesionados, no todo el que aparece. Con dobles en memoria — que
 * el aspecto envuelva de verdad los beans y publique al outbox lo cubre `InjuryTagAuditAccessIntegrationTest`.
 */
class InjuryTagAuditTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val activo = valor("activo")
        val lesion = valor("lesión").copy(healthData = true, reservedRole = ReservedRole.LESION)
        val postParto = valor("post-parto").copy(healthData = true)
        val medio = valor("medio")
        val taxonomy =
            Taxonomy.rehydrate(
                club,
                listOf(clave(club, "nivel", listOf(medio)), clave(club, "estado", listOf(activo, lesion, postParto))),
            )
        val sinEjeEstado = Taxonomy.rehydrate(club, listOf(clave(club, "nivel", listOf(medio))))
        val lesionado = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val sano = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val enPostParto = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        context("listado de alumnos") {
            val alumnos =
                listOf(
                    alumno(lesionado, setOf(medio.id, lesion.id)),
                    alumno(sano, setOf(medio.id, activo.id)),
                    alumno(enPostParto, setOf(postParto.id)),
                )

            test("audita solo a quien sale con algun valor de salud, lesion o post-parto") {
                ListStudentsQuery(
                    InMemoryStudentDirectory(alumnos),
                    InMemoryTaxonomyRepository(taxonomy),
                    mockk(relaxed = true),
                ).execute(coach, emptyList())
                    .shouldBeRight()
                    .auditSubjectIds() shouldBe setOf(lesionado.value, enPostParto.value)
            }

            test("sin valores de salud en la taxonomia no audita a nadie") {
                ListStudentsQuery(
                    InMemoryStudentDirectory(alumnos),
                    InMemoryTaxonomyRepository(sinEjeEstado),
                    mockk(relaxed = true),
                ).execute(coach, emptyList())
                    .shouldBeRight()
                    .auditSubjectIds()
                    .shouldBeEmpty()
            }
        }

        context("tags de un alumno") {
            fun query(asignados: Set<TagValueId>): ListStudentTagsQuery {
                val repo = InMemoryTaxonomyRepository(taxonomy)
                val tags = InMemoryStudentTagRepository(mutableMapOf(lesionado to asignados.toMutableSet()))
                val classification =
                    StudentClassification(
                        mockk<StudentLookup> { every { isStudent(any(), any()) } returns true },
                        tags,
                        repo,
                        mockk<GroupRepository>(relaxed = true),
                        mockk<GroupMembershipPublisher>(relaxed = true),
                        mockk(relaxed = true),
                        InMemoryHealthConsentReader(),
                    )
                return ListStudentTagsQuery(classification, repo, mockk(relaxed = true))
            }

            test("audita al alumno si lleva el tag de lesion") {
                query(
                    setOf(medio.id, lesion.id),
                ).execute(coach, lesionado.value).shouldBeRight().auditSubjectIds() shouldBe
                    setOf(lesionado.value)
            }

            test("no audita si no lo lleva") {
                query(
                    setOf(medio.id, activo.id),
                ).execute(coach, lesionado.value).shouldBeRight().auditSubjectIds().shouldBeEmpty()
            }
        }

        context("detalle de un grupo") {
            val incluidoAMano = PersonId.of(UuidCreator.getTimeOrderedEpoch())
            val excluidoQueLoCumple = PersonId.of(UuidCreator.getTimeOrderedEpoch())
            val excluidoQueNoLoCumple = PersonId.of(UuidCreator.getTimeOrderedEpoch())

            fun detalle(group: Group) =
                GroupDetail(
                    group,
                    members =
                        listOf(
                            GroupMembership(
                                GroupMember(lesionado, "Ana"),
                                GroupMemberOrigin.FILTER,
                                hasOverride = false,
                            ),
                            GroupMembership(
                                GroupMember(incluidoAMano, "Luis"),
                                GroupMemberOrigin.MANUAL_INCLUSION,
                                hasOverride = true,
                            ),
                        ),
                    exclusions =
                        listOf(
                            GroupExclusion(GroupMember(excluidoQueLoCumple, "Eva"), matchesFilter = true),
                            GroupExclusion(GroupMember(excluidoQueNoLoCumple, "Iker"), matchesFilter = false),
                        ),
                )

            fun execute(group: Group) =
                GetGroupDetailQuery(
                    InMemoryGroupRepository(existing = mapOf(group.id to detalle(group))),
                    InMemoryTaxonomyRepository(taxonomy),
                    mockk(relaxed = true),
                ).execute(coach, group.id.value).shouldBeRight()

            test("si el filtro exige lesion audita a los miembros por filtro y a los excluidos que lo cumplen") {
                val lesionados = Group.create(club, "Readaptación", setOf(lesion.id)).shouldBeRight()

                execute(lesionados).auditSubjectIds() shouldBe setOf(lesionado.value, excluidoQueLoCumple.value)
            }

            test("si el filtro no exige lesion no audita a nadie") {
                val porNivel = Group.create(club, "Medio", setOf(medio.id)).shouldBeRight()

                execute(porNivel).auditSubjectIds().shouldBeEmpty()
            }
        }

        context("previsualizacion de un filtro") {
            val previsualizacion = GroupMembers(listOf(GroupMember(lesionado, "Ana")))

            fun execute(filtro: List<TagValueId>) =
                PreviewGroupMembersQuery(
                    InMemoryTaxonomyRepository(taxonomy),
                    InMemoryGroupRepository(preview = previsualizacion),
                    mockk(relaxed = true),
                ).execute(coach, filtro.map { it.value }).shouldBeRight()

            test("un filtro con lesion audita a todos los devueltos") {
                execute(listOf(lesion.id)).auditSubjectIds() shouldBe setOf(lesionado.value)
            }

            test("un filtro sin lesion no audita") {
                execute(listOf(medio.id)).auditSubjectIds().shouldBeEmpty()
            }
        }
    })

private fun alumno(
    id: PersonId,
    tags: Set<TagValueId>,
) = StudentSummary(
    id = id,
    name = "Alumno",
    email = "alumno@club.test",
    status = PersonStatus.ACTIVO,
    tagValueIds = tags,
)

private fun valor(label: String) =
    TagValue(
        id = TagValueId.new(),
        label = TagLabel.forValue(label).getOrNull()!!,
        metadata = TagValueMetadata.Empty,
        archivedAt = null,
    )

private fun clave(
    club: ClubId,
    label: String,
    values: List<TagValue>,
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    type = TagKeyType.SIMPLE,
    archivedAt = null,
    values = values,
)
