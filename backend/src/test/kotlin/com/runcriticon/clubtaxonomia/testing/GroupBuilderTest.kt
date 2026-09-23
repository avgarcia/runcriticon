package com.runcriticon.clubtaxonomia.testing

import com.runcriticon.testing.TestClubs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GroupBuilderTest :
    FunSpec({
        test("GroupBuilder crea un grupo válido con valores por defecto") {
            val group = GroupBuilder().build()

            group.name.value shouldBe "Grupo de prueba"
            group.requiredTagValueIds shouldBe emptySet()
        }

        test("GroupBuilder respeta el club e id indicados") {
            val club = TestClubs.newClub()

            val group = GroupBuilder().inClub(club).named("Maratón avanzado").build()

            group.clubId shouldBe club
            group.name.value shouldBe "Maratón avanzado"
        }
    })
