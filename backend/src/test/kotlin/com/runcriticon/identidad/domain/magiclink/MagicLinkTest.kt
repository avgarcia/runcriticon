package com.runcriticon.identidad.domain.magiclink

import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MagicLinkTest :
    FunSpec({
        val now = Instant.parse("2026-06-29T10:00:00Z")
        val tokenHash = TokenHash("hash-correcto")
        val otra = TokenHash("hash-distinto")

        fun link(
            expiresAt: Instant = now.plus(Duration.ofMinutes(15)),
            consumedAt: Instant? = null,
            hash: TokenHash = tokenHash,
            proposito: MagicLinkPurpose = MagicLinkPurpose.LOGIN,
        ) = MagicLink(
            id = MagicLinkId.new(),
            userId = UserId.new(),
            clubId = ClubId.of(UUID.randomUUID()),
            tokenHash = hash,
            proposito = proposito,
            issuedAt = now,
            expiresAt = expiresAt,
            consumedAt = consumedAt,
        )

        test("issue caduca a los 15 minutos, queda abierto y con el propósito indicado") {
            val ml =
                MagicLink.issue(
                    UserId.new(),
                    ClubId.of(UUID.randomUUID()),
                    tokenHash,
                    MagicLinkPurpose.RESETEO,
                    now,
                )
            Duration.between(ml.issuedAt, ml.expiresAt) shouldBe Duration.ofMinutes(15)
            ml.consumedAt shouldBe null
            ml.proposito shouldBe MagicLinkPurpose.RESETEO
        }

        test("consume con token correcto, propósito correcto y no caducado marca consumido") {
            val consumed = link().consume(MagicLinkPurpose.LOGIN, tokenHash, now.plusSeconds(60)).shouldBeRight()
            consumed.consumedAt shouldBe now.plusSeconds(60)
        }

        test("consume con propósito distinto se rechaza (token RESETEO no vale como LOGIN)") {
            link(proposito = MagicLinkPurpose.RESETEO)
                .consume(MagicLinkPurpose.LOGIN, tokenHash, now.plusSeconds(60))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()
        }

        test("consume con propósito distinto se rechaza (token LOGIN no vale como RESETEO)") {
            link(proposito = MagicLinkPurpose.LOGIN)
                .consume(MagicLinkPurpose.RESETEO, tokenHash, now.plusSeconds(60))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()
        }

        test("consume caducado (>15 min) devuelve InvalidInput") {
            link()
                .consume(MagicLinkPurpose.LOGIN, tokenHash, now.plus(Duration.ofMinutes(16)))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()
        }

        test("consume de un enlace ya usado devuelve Conflict") {
            link(consumedAt = now.plusSeconds(5))
                .consume(MagicLinkPurpose.LOGIN, tokenHash, now.plusSeconds(60))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()
        }

        test("consume con token que no coincide devuelve InvalidInput") {
            link()
                .consume(MagicLinkPurpose.LOGIN, otra, now.plusSeconds(60))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()
        }
    })
