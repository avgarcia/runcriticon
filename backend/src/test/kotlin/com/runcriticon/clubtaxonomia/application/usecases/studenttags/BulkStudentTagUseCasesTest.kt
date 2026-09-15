package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento de la clasificación en masa con la base de datos sustituida por dobles: qué alumnos cuentan como
 * actualizados, cuándo se rechaza la operación entera y qué queda en la auditoría.
 */
class BulkStudentTagUseCasesTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val medio = valor("medio")
        val montanaArchivada = valor("montaña", archived = true)
        val nivel = clave(club, "nivel", listOf(medio))
        val terreno = clave(club, "terreno", listOf(montanaArchivada))

        val alumnoA = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val alumnoB = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val alumnoC = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        lateinit var tags: InMemoryStudentTagRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository
        lateinit var audit: BulkRecordingAuditTrail
        lateinit var classification: BulkStudentClassification
        lateinit var assign: AssignStudentTagInBulkCommand
        lateinit var unassign: UnassignStudentTagInBulkCommand

        fun classificationWith(lookup: StudentLookup) =
            BulkStudentClassification(
                lookup,
                tags,
                taxonomy,
                mockk<GroupRepository>(relaxed = true),
                mockk<GroupMembershipPublisher>(relaxed = true),
                audit,
            )

        beforeEach {
            tags = InMemoryStudentTagRepository()
            taxonomy = InMemoryTaxonomyRepository(Taxonomy.rehydrate(club, listOf(nivel, terreno)))
            audit = BulkRecordingAuditTrail()
            classification = classificationWith(AlwaysBulkStudent)
            assign = AssignStudentTagInBulkCommand(classification, tags)
            unassign = UnassignStudentTagInBulkCommand(classification, tags)
        }

        test("asignar un valor a varios alumnos se lo deja a los tres") {
            val actualizados =
                assign
                    .execute(admin, listOf(alumnoA.value, alumnoB.value, alumnoC.value), medio.id.value)
                    .shouldBeRight()

            actualizados shouldBe 3
            setOf(alumnoA, alumnoB, alumnoC).forEach { tags.findAssignedValueIds(club, it) shouldBe setOf(medio.id) }
        }

        test("quien ya tenia el valor no cuenta como actualizado") {
            tags.add(club, alumnoA, medio.id)

            val actualizados =
                assign
                    .execute(admin, listOf(alumnoA.value, alumnoB.value, alumnoC.value), medio.id.value)
                    .shouldBeRight()

            actualizados shouldBe 2
        }

        test("quitar un valor solo retira a quien lo tenia, sin fallar por los demas") {
            tags.add(club, alumnoA, medio.id)
            tags.add(club, alumnoB, medio.id)

            val actualizados =
                unassign
                    .execute(admin, listOf(alumnoA.value, alumnoB.value, alumnoC.value), medio.id.value)
                    .shouldBeRight()

            actualizados shouldBe 2
            tags.findAssignedValueIds(club, alumnoC) shouldBe emptySet()
        }

        test("un valor archivado que ninguno tenia se rechaza sin escribir") {
            val error =
                assign.execute(admin, listOf(alumnoA.value, alumnoB.value), montanaArchivada.id.value).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.Conflict>().reason shouldBe "tag_value_not_assignable"
            tags.writeCount shouldBe 0
        }

        test("un valor archivado que todos tenian ya se conserva sin contar actualizaciones") {
            tags.add(club, alumnoA, montanaArchivada.id)
            tags.add(club, alumnoB, montanaArchivada.id)

            val actualizados =
                assign
                    .execute(admin, listOf(alumnoA.value, alumnoB.value), montanaArchivada.id.value)
                    .shouldBeRight()

            actualizados shouldBe 0
        }

        test("un valor archivado que solo algunos tenian se rechaza para todos") {
            tags.add(club, alumnoA, montanaArchivada.id)
            val writesAntes = tags.writeCount

            val error =
                assign.execute(admin, listOf(alumnoA.value, alumnoB.value), montanaArchivada.id.value).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.Conflict>().reason shouldBe "tag_value_not_assignable"
            tags.writeCount shouldBe writesAntes
        }

        test("un id que no es alumno del club rechaza la operacion entera") {
            val parcial = classificationWith(PartiallyBulkStudent)
            val comando = AssignStudentTagInBulkCommand(parcial, tags)

            comando.execute(admin, listOf(alumnoA.value, alumnoB.value), medio.id.value).shouldBeLeft(
                ClubTaxonomiaError.StudentNotFound,
            )
            tags.writeCount shouldBe 0
        }

        test("los ids repetidos se colapsan y el recuento nunca pasa del numero de alumnos distintos") {
            val actualizados =
                assign
                    .execute(admin, listOf(alumnoA.value, alumnoA.value, alumnoB.value), medio.id.value)
                    .shouldBeRight()

            actualizados shouldBe 2
        }

        test("una lista vacia se rechaza como entrada invalida") {
            assign.execute(admin, emptyList(), medio.id.value).shouldBeLeft(
                ClubTaxonomiaError.InvalidInput(field = "alumnos", reason = "empty"),
            )
        }

        test("hay un asiento de auditoria por alumno cambiado, y ninguno para quien no cambia") {
            tags.add(club, alumnoC, medio.id)

            assign
                .execute(admin, listOf(alumnoA.value, alumnoB.value, alumnoC.value), medio.id.value)
                .shouldBeRight()

            audit.entries.map { it.subjectId }.toSet() shouldBe setOf(alumnoA.value, alumnoB.value)
        }

        test("el recuento devuelto coincide siempre con los asientos de auditoria escritos") {
            tags.add(club, alumnoC, medio.id)

            val actualizados =
                assign
                    .execute(admin, listOf(alumnoA.value, alumnoB.value, alumnoC.value), medio.id.value)
                    .shouldBeRight()

            actualizados shouldBe audit.entries.size
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
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    archivedAt = null,
    values = values,
)

/** Doble en memoria del puerto de auditoría: guarda cada asiento tal cual llega, para comprobar su contenido. */
private class BulkRecordingAuditTrail : AuditTrail {
    val entries = mutableListOf<AuditEntry>()

    override fun record(
        clubId: ClubId,
        entry: AuditEntry,
    ) {
        entries += entry
    }

    override fun anonymize(personId: UUID): Int = error("no usado en este test")
}

/** El caso normal: todos los seleccionados son alumnos del club. */
private object AlwaysBulkStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true

    override fun lockStudents(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Int = studentIds.size
}

/**
 * Solo uno de los seleccionados no es alumno del club — el caso normal de una operación en masa que falla: no todos
 * a la vez, sino uno solo entre varios válidos.
 */
private object PartiallyBulkStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true

    override fun lockStudents(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Int = (studentIds.size - 1).coerceAtLeast(0)
}
