package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.application.usecases.groups.InMemoryGroupRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.InMemoryStudentTagRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * Impacto de archivar (LAL-83): cuántos alumnos lo tienen asignado (informativo) y qué grupos vivos lo requieren
 * (bloqueante), incluido el caso borde de ADR-0002 D3 en el que un grupo se quedaría sin ningún tag requerido activo.
 */
class TagArchiveImpactQueryTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)

        lateinit var repository: InMemoryTaxonomyRepository
        lateinit var studentTagRepository: InMemoryStudentTagRepository

        beforeTest {
            repository = InMemoryTaxonomyRepository(Taxonomy.empty(clubId))
            studentTagRepository = InMemoryStudentTagRepository()
        }

        test("sin alumnos ni grupos, el impacto de un valor sin uso es cero") {
            val keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Nivel")
                    .shouldBeRight()
                    .id.value
            val value = AddTagValueCommand(repository).execute(admin, keyId, "Principiante").shouldBeRight()

            val impact =
                GetTagValueArchiveImpactQuery(repository, studentTagRepository, InMemoryGroupRepository())
                    .execute(admin, value.id.value)
                    .shouldBeRight()

            impact.studentsAffected shouldBe 0
            impact.groupsRequiring shouldBe emptyList()
        }

        test("cuenta los alumnos con el valor asignado, sin duplicar por alumno") {
            val keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Nivel")
                    .shouldBeRight()
                    .id.value
            val value = AddTagValueCommand(repository).execute(admin, keyId, "Principiante").shouldBeRight()
            studentTagRepository.add(clubId, PersonId.of(UUID.randomUUID()), value.id)
            studentTagRepository.add(clubId, PersonId.of(UUID.randomUUID()), value.id)

            val impact =
                GetTagValueArchiveImpactQuery(repository, studentTagRepository, InMemoryGroupRepository())
                    .execute(admin, value.id.value)
                    .shouldBeRight()

            impact.studentsAffected shouldBe 2
        }

        test("un grupo con otros tags requeridos además del archivado no perdería todo su filtro") {
            val keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Nivel")
                    .shouldBeRight()
                    .id.value
            val value = AddTagValueCommand(repository).execute(admin, keyId, "Principiante").shouldBeRight()
            val otherKeyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Objetivo")
                    .shouldBeRight()
                    .id.value
            val otherValue = AddTagValueCommand(repository).execute(admin, otherKeyId, "5K").shouldBeRight()
            val group = Group.create(clubId, "Iniciación 5K", setOf(value.id, otherValue.id)).shouldBeRight()
            val groupRepository =
                InMemoryGroupRepository(existing = mapOf(group.id to GroupDetail(group, emptyList(), emptyList())))

            val impact =
                GetTagValueArchiveImpactQuery(repository, studentTagRepository, groupRepository)
                    .execute(admin, value.id.value)
                    .shouldBeRight()

            val affected = impact.groupsRequiring.single()
            affected.groupId shouldBe group.id
            affected.wouldLoseAllRequiredTags shouldBe false
        }

        test("un grupo cuyo único tag requerido es el archivado se quedaría sin filtro activo") {
            val keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Nivel")
                    .shouldBeRight()
                    .id.value
            val value = AddTagValueCommand(repository).execute(admin, keyId, "Principiante").shouldBeRight()
            val group = Group.create(clubId, "Solo principiantes", setOf(value.id)).shouldBeRight()
            val groupRepository =
                InMemoryGroupRepository(existing = mapOf(group.id to GroupDetail(group, emptyList(), emptyList())))

            val impact =
                GetTagValueArchiveImpactQuery(repository, studentTagRepository, groupRepository)
                    .execute(admin, value.id.value)
                    .shouldBeRight()

            impact.groupsRequiring.single().wouldLoseAllRequiredTags shouldBe true
        }

        test("el impacto de un eje agrega el de todos sus valores") {
            val keyId =
                CreateTagKeyCommand(repository)
                    .execute(admin, "Nivel")
                    .shouldBeRight()
                    .id.value
            val principiante = AddTagValueCommand(repository).execute(admin, keyId, "Principiante").shouldBeRight()
            val avanzado = AddTagValueCommand(repository).execute(admin, keyId, "Avanzado").shouldBeRight()
            studentTagRepository.add(clubId, PersonId.of(UUID.randomUUID()), principiante.id)
            studentTagRepository.add(clubId, PersonId.of(UUID.randomUUID()), avanzado.id)
            val group = Group.create(clubId, "Avanzados", setOf(avanzado.id)).shouldBeRight()
            val groupRepository =
                InMemoryGroupRepository(existing = mapOf(group.id to GroupDetail(group, emptyList(), emptyList())))

            val impact =
                GetTagKeyArchiveImpactQuery(repository, studentTagRepository, groupRepository)
                    .execute(admin, keyId)
                    .shouldBeRight()

            impact.studentsAffected shouldBe 2
            impact.groupsRequiring.single().groupId shouldBe group.id
        }

        test("impacto de un valor inexistente devuelve TagValueNotFound") {
            GetTagValueArchiveImpactQuery(repository, studentTagRepository, InMemoryGroupRepository())
                .execute(admin, UUID.randomUUID())
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
        }

        test("impacto de un eje inexistente devuelve TagKeyNotFound") {
            GetTagKeyArchiveImpactQuery(repository, studentTagRepository, InMemoryGroupRepository())
                .execute(admin, UUID.randomUUID())
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)
        }
    })
