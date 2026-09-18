package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.InMemoryStudentTagRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.api.events.LesionDeclarada
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento de [LesionDeclaradaListener] (LAL-131) con la persistencia sustituida por dobles: qué eje
 * muta, qué reemplaza, y cuándo se abstiene sin fallar.
 */
class LesionDeclaradaListenerTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val alumno = PersonId.of(UUID.randomUUID())
        val activo = valor("activo")
        val lesion = valor("lesión")
        val descanso = valor("descanso")
        val estadoKey = clave(club, "estado", listOf(activo, lesion, descanso))

        lateinit var tags: InMemoryStudentTagRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository
        lateinit var listener: LesionDeclaradaListener

        fun newListener(
            taxonomyOverride: Taxonomy = Taxonomy.rehydrate(club, listOf(estadoKey)),
        ): LesionDeclaradaListener {
            tags = InMemoryStudentTagRepository()
            taxonomy = InMemoryTaxonomyRepository(taxonomyOverride)
            val classification =
                StudentClassification(
                    AlwaysStudent,
                    tags,
                    taxonomy,
                    mockk<GroupRepository>(relaxed = true),
                    mockk<GroupMembershipPublisher>(relaxed = true),
                    mockk<AuditTrail>(relaxed = true),
                )
            return LesionDeclaradaListener(
                classification = classification,
                studentTags = tags,
                processedEvents = InMemoryProcessedEventTracker(),
                mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
            )
        }

        fun event() =
            LesionDeclarada(
                eventId = UUID.randomUUID(),
                aggregateId = alumno.value,
                occurredAt = Instant.parse("2026-09-18T10:00:00Z"),
                clubId = club.value,
                actorId = alumno.value,
                traceparent = null,
            )

        test("asigna lesion y quita cualquier otro valor del eje estado") {
            listener = newListener()
            tags.add(club, alumno, activo.id)

            listener.on(event())

            tags.findAssignedValueIds(club, alumno) shouldBe setOf(lesion.id)
        }

        test("un alumno sin ningun estado previo termina solo con lesion") {
            listener = newListener()

            listener.on(event())

            tags.findAssignedValueIds(club, alumno) shouldBe setOf(lesion.id)
        }

        test("un alumno que ya tenia lesion no cambia (idempotente)") {
            listener = newListener()
            tags.add(club, alumno, lesion.id)

            listener.on(event())

            tags.findAssignedValueIds(club, alumno) shouldBe setOf(lesion.id)
        }

        test("una taxonomia sin el eje estado no falla y no muta nada") {
            listener = newListener(taxonomyOverride = Taxonomy.empty(club))
            tags.add(club, alumno, activo.id)

            listener.on(event())

            tags.findAssignedValueIds(club, alumno) shouldBe setOf(activo.id)
        }

        test("un valor lesion archivado no se asigna") {
            val lesionArchivada = valor("lesión", archived = true)
            val estadoConLesionArchivada = clave(club, "estado", listOf(activo, lesionArchivada))
            listener = newListener(taxonomyOverride = Taxonomy.rehydrate(club, listOf(estadoConLesionArchivada)))
            tags.add(club, alumno, activo.id)

            listener.on(event())

            tags.findAssignedValueIds(club, alumno) shouldBe setOf(activo.id)
        }

        test("reentregar el mismo evento no vuelve a mutar") {
            listener = newListener()
            tags.add(club, alumno, activo.id)
            val evt = event()

            listener.on(evt)
            val writeCountTrasPrimeraEntrega = tags.writeCount
            listener.on(evt)

            tags.writeCount shouldBe writeCountTrasPrimeraEntrega
        }
    })

private object AlwaysStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true

    override fun lockStudents(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Int = studentIds.size
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
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    type = TagKeyType.SIMPLE,
    archivedAt = null,
    values = values,
)
