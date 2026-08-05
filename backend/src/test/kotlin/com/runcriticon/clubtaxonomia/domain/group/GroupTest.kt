package com.runcriticon.clubtaxonomia.domain.group

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class GroupTest :
    FunSpec({
        val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())

        test("crea un grupo válido con nombre recortado") {
            val group = Group.create(club, "  Maratón Valencia avanzado  ").shouldBeRight()

            group.clubId shouldBe club
            group.name.value shouldBe "Maratón Valencia avanzado"
        }

        test("un nombre en blanco tras trim devuelve InvalidInput blank") {
            Group.create(club, "   ").shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "blank"))
        }

        test("un nombre demasiado largo devuelve InvalidInput too_long") {
            val over = "a".repeat(GroupName.MAX_LENGTH + 1)

            Group.create(club, over).shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "too_long"))
        }

        test("el nombre en el límite exacto es válido") {
            Group.create(club, "a".repeat(GroupName.MAX_LENGTH)).shouldBeRight()
        }

        test("requiredTagValueIds vacío es válido -- caso borde de grupo sin tags requeridos") {
            val group = Group.create(club, "Solo incluidos manualmente").shouldBeRight()

            group.requiredTagValueIds.shouldBeEmpty()
        }

        test("conserva los tags requeridos pedidos") {
            val nivel = TagValueId.of(UuidCreator.getTimeOrderedEpoch())
            val objetivo = TagValueId.of(UuidCreator.getTimeOrderedEpoch())

            val group =
                Group
                    .create(club, "Grupo con filtro", requiredTagValueIds = setOf(nivel, objetivo))
                    .shouldBeRight()

            group.requiredTagValueIds shouldBe setOf(nivel, objetivo)
        }
    })
