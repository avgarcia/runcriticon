package com.runcriticon.identidad.domain.club

import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ClubTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val club = Club(id = clubId, name = "Mi club", slug = null)

        test("rename cambia el nombre tras trim") {
            val renamed = club.rename("  Club Runcriticon  ").shouldBeRight()
            renamed.name shouldBe "Club Runcriticon"
        }

        test("rename con nombre vacío tras trim devuelve InvalidInput") {
            club.rename("   ").shouldBeLeft(IdentidadError.InvalidInput("nombre", "blank"))
        }

        test("rename con nombre de más de 200 caracteres devuelve InvalidInput") {
            val tooLong = "a".repeat(Club.MAX_NAME_LENGTH + 1)
            club.rename(tooLong).shouldBeLeft(IdentidadError.InvalidInput("nombre", "too_long"))
        }

        test("rename con exactamente 200 caracteres es válido") {
            val exact = "a".repeat(Club.MAX_NAME_LENGTH)
            club.rename(exact).shouldBeRight().name shouldBe exact
        }
    })
