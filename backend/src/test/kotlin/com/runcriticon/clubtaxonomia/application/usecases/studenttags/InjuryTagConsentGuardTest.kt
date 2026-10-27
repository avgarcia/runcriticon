package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
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
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

/**
 * El tag `estado = lesión` solo se asigna a quien tiene consentimiento vigente de datos de salud (ADR-0014 D16/D18),
 * por las tres vías de un entrenador: asignar uno, reemplazar la clasificación y asignar en masa.
 */
class InjuryTagConsentGuardTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val conConsentimiento = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val sinConsentimiento = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val lesion = valor("lesión").copy(healthData = true, reservedRole = ReservedRole.LESION)
        val postParto = valor("post-parto").copy(healthData = true)
        val activo = valor("activo")
        // Un «lesión» sin marcar en otro eje: el nombre no lo convierte en dato de salud.
        val lesionSinMarcar = valor("lesión")
        val taxonomy =
            Taxonomy.rehydrate(
                club,
                listOf(
                    clave(club, "estado", listOf(activo, lesion, postParto)),
                    clave(club, "historial", listOf(lesionSinMarcar)),
                ),
            )
        val sinPermiso = ClubTaxonomiaError.Conflict("student_without_health_consent")

        lateinit var tags: InMemoryStudentTagRepository
        lateinit var assign: AssignStudentTagCommand
        lateinit var replace: ReplaceStudentTagsCommand
        lateinit var bulk: AssignStudentTagInBulkCommand

        beforeEach {
            tags = InMemoryStudentTagRepository()
            val repo = InMemoryTaxonomyRepository(taxonomy)
            val consent = InMemoryHealthConsentReader(revoked = setOf(sinConsentimiento))
            val lookup =
                mockk<StudentLookup> {
                    every { isStudent(any(), any()) } returns true
                    every { lockStudents(any(), any()) } answers { secondArg<Set<PersonId>>().size }
                }
            val groups = mockk<GroupRepository>(relaxed = true)
            val publisher = mockk<GroupMembershipPublisher>(relaxed = true)
            val classification =
                StudentClassification(lookup, tags, repo, groups, publisher, mockk(relaxed = true), consent)
            assign = AssignStudentTagCommand(classification, tags, mockk(relaxed = true))
            replace = ReplaceStudentTagsCommand(classification, tags, mockk(relaxed = true))
            bulk =
                AssignStudentTagInBulkCommand(
                    BulkStudentClassification(lookup, tags, repo, groups, publisher, mockk(relaxed = true), consent),
                    tags,
                    mockk(relaxed = true),
                )
        }

        test("asignar lesion a un alumno sin consentimiento se rechaza y no escribe nada") {
            assign.execute(coach, sinConsentimiento.value, lesion.id.value).shouldBeLeft(sinPermiso)

            tags.findAssignedValueIds(club, sinConsentimiento) shouldBe emptySet()
        }

        test("cualquier valor de salud lo exige, no solo lesion: post-parto sin consentimiento se rechaza") {
            assign.execute(coach, sinConsentimiento.value, postParto.id.value).shouldBeLeft(sinPermiso)
        }

        test("un valor llamado lesion sin marcar como dato de salud no exige consentimiento") {
            assign.execute(coach, sinConsentimiento.value, lesionSinMarcar.id.value).shouldBeRight()
        }

        test("asignar lesion a un alumno con consentimiento funciona") {
            assign.execute(coach, conConsentimiento.value, lesion.id.value).shouldBeRight()

            tags.findAssignedValueIds(club, conConsentimiento) shouldBe setOf(lesion.id)
        }

        test("otros valores del eje estado no exigen consentimiento") {
            assign.execute(coach, sinConsentimiento.value, activo.id.value).shouldBeRight()
        }

        test("reemplazar la clasificacion anadiendo lesion sin consentimiento se rechaza") {
            replace.execute(coach, sinConsentimiento.value, listOf(lesion.id.value)).shouldBeLeft(sinPermiso)
        }

        test("reemplazar conservando una lesion que ya llevaba no falla (su retirada va por la revocacion)") {
            tags.add(club, sinConsentimiento, lesion.id)

            replace.execute(coach, sinConsentimiento.value, listOf(lesion.id.value)).shouldBeRight()
        }

        test("en masa, si alguno de los que no la llevaban no tiene consentimiento, no se aplica a ninguno") {
            bulk
                .execute(coach, listOf(conConsentimiento.value, sinConsentimiento.value), lesion.id.value)
                .shouldBeLeft(sinPermiso)

            tags.findAssignedValueIds(club, conConsentimiento) shouldBe emptySet()
        }

        test("en masa, con consentimiento de todos, se aplica") {
            bulk.execute(coach, listOf(conConsentimiento.value), lesion.id.value).shouldBeRight() shouldBe 1
        }
    })

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
