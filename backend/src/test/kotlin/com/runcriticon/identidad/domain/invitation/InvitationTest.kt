package com.runcriticon.identidad.domain.invitation

import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant

class InvitationTest :
    FunSpec({
        val now = Instant.parse("2026-06-19T10:00:00Z")
        val within = now.plus(Duration.ofMinutes(1))
        val later = now.plus(Duration.ofMinutes(2))
        val afterExpiry = now.plus(Invitation.DEFAULT_TTL).plus(Duration.ofMinutes(1))

        val userId = UserId.new()
        val tokenHash = TokenHash("hash-correcto")
        val otherHash = TokenHash("hash-distinto")

        test("issue deja la invitación abierta y la caduca a los 7 días (ADR-0003 D4)") {
            val invitation = Invitation.issue(userId, tokenHash, now)
            invitation.issuedAt shouldBe now
            invitation.expiresAt shouldBe now.plus(Invitation.DEFAULT_TTL)
            invitation.consumedAt shouldBe null
        }

        test("issue con ttl no positivo es una precondición imposible (ADR-0008)") {
            shouldThrow<IllegalArgumentException> {
                Invitation.issue(userId, tokenHash, now, Duration.ZERO)
            }
        }

        test("consume con el hash correcto antes de caducar marca la invitación") {
            val consumed =
                Invitation
                    .issue(userId, tokenHash, now)
                    .consume(tokenHash, within)
                    .shouldBeRight()
            consumed.consumedAt shouldBe within
        }

        test("una invitación ya consumida no admite un segundo uso (un solo uso, D4)") {
            val consumed =
                Invitation.issue(userId, tokenHash, now).consume(tokenHash, within).shouldBeRight()
            consumed
                .consume(tokenHash, later)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()
        }

        test("consume después de la caducidad falla (D4)") {
            val error =
                Invitation
                    .issue(userId, tokenHash, now)
                    .consume(tokenHash, afterExpiry)
                    .shouldBeLeft()
            error.shouldBeInstanceOf<IdentidadError.InvalidInput>().reason shouldBe "expired"
        }

        test("consume con un token que no coincide falla (verificación con tokenHash)") {
            val error =
                Invitation
                    .issue(userId, tokenHash, now)
                    .consume(otherHash, within)
                    .shouldBeLeft()
            error.shouldBeInstanceOf<IdentidadError.InvalidInput>().reason shouldBe "mismatch"
        }

        test("reissue invalida la invitación anterior y emite una nueva utilizable (D4)") {
            val (invalidated, fresh) =
                Invitation.issue(userId, tokenHash, now).reissue(otherHash, within)

            invalidated
                .consume(tokenHash, later)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()
            fresh.consume(otherHash, later).shouldBeRight()
            fresh.id shouldNotBe invalidated.id
        }
    })
