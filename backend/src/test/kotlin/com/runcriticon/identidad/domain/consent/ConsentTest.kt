package com.runcriticon.identidad.domain.consent

import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class ConsentTest :
    FunSpec({
        val userId = UserId.new()
        val clubId = ClubId.of(UUID.randomUUID())
        val grantedAt = Instant.parse("2026-08-25T10:00:00Z")

        test("grant crea una concesion activa, sin revocar") {
            val consent =
                Consent.grant(
                    userId = userId,
                    clubId = clubId,
                    textVersion = ConsentText.CURRENT_VERSION,
                    ip = "203.0.113.10",
                    userAgent = "test-agent",
                    now = grantedAt,
                )

            consent.isActive() shouldBe true
            consent.revokedAt shouldBe null
            consent.grantedAt shouldBe grantedAt
            consent.textVersion shouldBe ConsentText.CURRENT_VERSION
        }

        test("revoke rellena revokedAt sin tocar el resto de la fila") {
            val consent =
                Consent.grant(userId, clubId, ConsentText.CURRENT_VERSION, "203.0.113.10", "test-agent", grantedAt)
            val revokedAt = grantedAt.plusSeconds(3600)

            val revoked = consent.revoke(revokedAt)

            revoked.isActive() shouldBe false
            revoked.revokedAt shouldBe revokedAt
            // Nada más cambia: mismo id, mismo grantedAt, misma ip/userAgent de la concesión original.
            revoked.id shouldBe consent.id
            revoked.grantedAt shouldBe consent.grantedAt
            revoked.ip shouldBe consent.ip
        }

        test("revocar una fila ya revocada es un invariante violado") {
            val consent =
                Consent
                    .grant(userId, clubId, ConsentText.CURRENT_VERSION, "203.0.113.10", "test-agent", grantedAt)
                    .revoke(grantedAt.plusSeconds(60))

            shouldThrow<IllegalArgumentException> { consent.revoke(grantedAt.plusSeconds(120)) }
        }
    })
