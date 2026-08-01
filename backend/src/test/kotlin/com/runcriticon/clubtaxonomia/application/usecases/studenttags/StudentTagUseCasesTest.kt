package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento de la clasificación con la base de datos sustituida por dobles: qué se guarda, qué se rechaza y qué
 * es inocuo repetir.
 */
class StudentTagUseCasesTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val alumno = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        val medio = valor("medio")
        val maraton = valor("maratón valencia")
        val media = valor("media madrid")
        val montanaArchivada = valor("montaña", archived = true)
        val nivel = clave(club, "nivel", listOf(medio))
        val objetivo = clave(club, "objetivo", listOf(maraton, media))
        val terreno = clave(club, "terreno", listOf(montanaArchivada))

        lateinit var tags: InMemoryStudentTagRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository
        lateinit var classification: StudentClassification
        lateinit var replace: ReplaceStudentTagsCommand
        lateinit var assign: AssignStudentTagCommand
        lateinit var unassign: UnassignStudentTagCommand
        lateinit var list: ListStudentTagsQuery

        beforeEach {
            tags = InMemoryStudentTagRepository()
            taxonomy = InMemoryTaxonomyRepository(Taxonomy.rehydrate(club, listOf(nivel, objetivo, terreno)))
            classification = StudentClassification(AlwaysStudent, tags, taxonomy)
            replace = ReplaceStudentTagsCommand(classification, tags)
            assign = AssignStudentTagCommand(classification, tags)
            unassign = UnassignStudentTagCommand(classification, tags)
            list = ListStudentTagsQuery(classification)
        }

        test("asignar varios valores los guarda todos") {
            val result =
                replace.execute(admin, alumno.value, listOf(medio.id.value, maraton.id.value)).shouldBeRight()

            result.assigned.map { it.value.id } shouldBe listOf(medio.id, maraton.id)
        }

        test("un alumno puede llevar dos valores del mismo eje") {
            val result =
                replace.execute(admin, alumno.value, listOf(maraton.id.value, media.id.value)).shouldBeRight()

            result.assigned shouldHaveSize 2
            result.assigned.map { it.keyId }.toSet() shouldBe setOf(objetivo.id)
        }

        test("reemplazar por una lista vacia borra toda la clasificacion") {
            replace.execute(admin, alumno.value, listOf(medio.id.value)).shouldBeRight()

            replace
                .execute(admin, alumno.value, emptyList())
                .shouldBeRight()
                .assigned
                .shouldBeEmpty()
        }

        test("los ids repetidos se colapsan") {
            val result =
                replace.execute(admin, alumno.value, listOf(medio.id.value, medio.id.value)).shouldBeRight()

            result.assigned shouldHaveSize 1
        }

        test("reemplazar dos veces por lo mismo no cambia el resultado") {
            val primero = replace.execute(admin, alumno.value, listOf(medio.id.value)).shouldBeRight()
            val segundo = replace.execute(admin, alumno.value, listOf(medio.id.value)).shouldBeRight()

            segundo.assigned.map { it.value.id } shouldBe primero.assigned.map { it.value.id }
        }

        test("un valor archivado que el alumno no tenia se rechaza") {
            val error =
                replace.execute(admin, alumno.value, listOf(montanaArchivada.id.value)).shouldBeLeft()

            error.shouldBeInstanceOf<ClubTaxonomiaError.Conflict>().reason shouldBe "tag_value_not_assignable"
            tags.writeCount shouldBe 0
        }

        /**
         * El caso que sostiene el formulario de clasificación: si al leer los chips actuales y volver a guardarlos sin
         * tocar nada el archivado diera error, la pantalla quedaría atascada sin forma de arreglarlo.
         */
        test("un valor archivado que el alumno YA tenia se conserva al reemplazar") {
            tags.add(club, alumno, montanaArchivada.id)

            val result =
                replace
                    .execute(admin, alumno.value, listOf(montanaArchivada.id.value, medio.id.value))
                    .shouldBeRight()

            result.assigned.map { it.value.id }.toSet() shouldBe setOf(montanaArchivada.id, medio.id)
        }

        test("un id que no existe en la taxonomia se rechaza como valor no encontrado") {
            val fantasma = UuidCreator.getTimeOrderedEpoch()

            replace.execute(admin, alumno.value, listOf(fantasma)).shouldBeLeft(
                ClubTaxonomiaError.TagValueNotFound,
            )
            tags.writeCount shouldBe 0
        }

        test("un valor de otro club se rechaza como no encontrado, sin revelar que existe") {
            val ajeno = valor("de otro club")
            // La taxonomía cargada es la del club del principal, así que un valor ajeno sencillamente no está.
            replace.execute(admin, alumno.value, listOf(ajeno.id.value)).shouldBeLeft(
                ClubTaxonomiaError.TagValueNotFound,
            )
        }

        test("asignar un valor suelto lo anade a lo que ya tenia") {
            replace.execute(admin, alumno.value, listOf(medio.id.value)).shouldBeRight()

            val result = assign.execute(admin, alumno.value, maraton.id.value).shouldBeRight()

            result.assigned.map { it.value.id }.toSet() shouldBe setOf(medio.id, maraton.id)
        }

        test("asignar dos veces el mismo valor no cambia nada ni falla") {
            assign.execute(admin, alumno.value, medio.id.value).shouldBeRight()

            assign.execute(admin, alumno.value, medio.id.value).shouldBeRight().assigned shouldHaveSize 1
        }

        test("reasignar un valor archivado que ya se tenia no falla") {
            tags.add(club, alumno, montanaArchivada.id)

            assign.execute(admin, alumno.value, montanaArchivada.id.value).shouldBeRight()
        }

        test("asignar un valor archivado que no se tenia se rechaza") {
            assign
                .execute(admin, alumno.value, montanaArchivada.id.value)
                .shouldBeLeft()
                .shouldBeInstanceOf<ClubTaxonomiaError.Conflict>()
        }

        test("quitar un valor lo desasigna") {
            replace.execute(admin, alumno.value, listOf(medio.id.value, maraton.id.value)).shouldBeRight()

            unassign.execute(admin, alumno.value, medio.id.value).shouldBeRight()

            list
                .execute(admin, alumno.value)
                .shouldBeRight()
                .assigned
                .map { it.value.id } shouldBe listOf(maraton.id)
        }

        test("quitar algo que el alumno no tenia no es un error") {
            unassign.execute(admin, alumno.value, medio.id.value).shouldBeRight()
        }

        test("un valor archivado siempre se puede quitar") {
            tags.add(club, alumno, montanaArchivada.id)

            unassign.execute(admin, alumno.value, montanaArchivada.id.value).shouldBeRight()

            list
                .execute(admin, alumno.value)
                .shouldBeRight()
                .assigned
                .shouldBeEmpty()
        }

        test("quitar un valor que ya no existe en la taxonomia tampoco falla") {
            unassign.execute(admin, alumno.value, UuidCreator.getTimeOrderedEpoch()).shouldBeRight()
        }

        test("consultar devuelve la clasificacion en el orden de la taxonomia") {
            replace.execute(admin, alumno.value, listOf(maraton.id.value, medio.id.value)).shouldBeRight()

            list
                .execute(admin, alumno.value)
                .shouldBeRight()
                .assigned
                .map { it.value.label.value } shouldBe
                listOf("medio", "maratón valencia")
        }

        context("cuando la persona no es un alumno del club") {
            lateinit var sinAlumno: StudentClassification

            beforeEach {
                sinAlumno = StudentClassification(NeverStudent, tags, taxonomy)
            }

            test("reemplazar devuelve alumno no encontrado y no escribe") {
                ReplaceStudentTagsCommand(sinAlumno, tags)
                    .execute(admin, alumno.value, listOf(medio.id.value))
                    .shouldBeLeft(ClubTaxonomiaError.StudentNotFound)

                tags.writeCount shouldBe 0
            }

            test("asignar devuelve alumno no encontrado") {
                AssignStudentTagCommand(sinAlumno, tags)
                    .execute(admin, alumno.value, medio.id.value)
                    .shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
            }

            test("quitar devuelve alumno no encontrado") {
                UnassignStudentTagCommand(sinAlumno, tags)
                    .execute(admin, alumno.value, medio.id.value)
                    .shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
            }

            test("consultar devuelve alumno no encontrado") {
                ListStudentTagsQuery(sinAlumno)
                    .execute(admin, alumno.value)
                    .shouldBeLeft(ClubTaxonomiaError.StudentNotFound)
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
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    archivedAt = null,
    values = values,
)
