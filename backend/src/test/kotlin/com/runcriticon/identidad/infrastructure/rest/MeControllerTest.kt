package com.runcriticon.identidad.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.usecases.consent.GrantConsentCommand
import com.runcriticon.identidad.application.usecases.consent.QueryMyConsentQuery
import com.runcriticon.identidad.application.usecases.consent.RevokeConsentCommand
import com.runcriticon.identidad.application.usecases.session.QueryMyPermissionsQuery
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentId
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.shared.api.rest.MiConsentimientoRequest
import com.runcriticon.shared.api.rest.MiConsentimientoResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

/**
 * Test unitario de [MeController]: verifica que delega en los casos de uso sin contexto Spring.
 * La autenticación real y el enrutamiento de Spring MVC se cubren en integración con Testcontainers.
 */
class MeControllerTest :
    FunSpec({
        val queryMyPermissions = mockk<QueryMyPermissionsQuery>()
        val queryMyConsent = mockk<QueryMyConsentQuery>()
        val grantConsent = mockk<GrantConsentCommand>()
        val revokeConsent = mockk<RevokeConsentCommand>()
        val principalProvider = mockk<PrincipalProvider>()
        val clientIpResolver = mockk<ClientIpResolver>(relaxed = true)
        val controller =
            MeController(
                queryMyPermissions,
                queryMyConsent,
                grantConsent,
                revokeConsent,
                principalProvider,
                clientIpResolver,
            )

        val alumno = Principal(userId = UUID.randomUUID(), clubId = ClubId.new().value, role = Role.ALUMNO)
        every { principalProvider.current() } returns alumno

        test("permissions devuelve el mapa del caso de uso tal cual") {
            val permissions = mapOf(Resource.COACH to setOf(Action.INVITE, Action.LIST))
            every { queryMyPermissions.execute() } returns permissions

            controller.permissions() shouldBe permissions
        }

        test("consent sin ninguna fila devuelve PENDIENTE") {
            every { queryMyConsent.execute(alumno) } returns null

            controller.consent() shouldBe MiConsentimientoResponse(estado = MiConsentimientoResponse.Estado.PENDIENTE)
        }

        test("consent con una fila vigente devuelve VIGENTE con su version y fecha") {
            val grantedAt = Instant.parse("2026-08-25T10:00:00Z")
            val consent =
                Consent(
                    id = ConsentId.new(),
                    userId = UserId.of(alumno.userId),
                    clubId = ClubId.of(alumno.clubId),
                    textVersion = ConsentText.CURRENT_VERSION,
                    grantedAt = grantedAt,
                    ip = "203.0.113.10",
                    userAgent = "test-agent",
                )
            every { queryMyConsent.execute(alumno) } returns consent

            val response = controller.consent()

            response.estado shouldBe MiConsentimientoResponse.Estado.VIGENTE
            response.versionTexto shouldBe ConsentText.CURRENT_VERSION
        }

        test("grant delega en GrantConsentCommand con la IP y el user-agent de la peticion") {
            val request = mockk<HttpServletRequest>()
            every { request.getHeader("User-Agent") } returns "chrome/1"
            every { clientIpResolver.resolve(request) } returns "203.0.113.20"
            val consent =
                Consent(
                    id = ConsentId.new(),
                    userId = UserId.of(alumno.userId),
                    clubId = ClubId.of(alumno.clubId),
                    textVersion = ConsentText.CURRENT_VERSION,
                    grantedAt = Instant.now(),
                    ip = "203.0.113.20",
                    userAgent = "chrome/1",
                )
            every {
                grantConsent.execute(alumno, ConsentText.CURRENT_VERSION, "203.0.113.20", "chrome/1")
            } returns consent.right()

            val resp =
                controller.grant(
                    MiConsentimientoRequest(versionConsentimiento = ConsentText.CURRENT_VERSION),
                    request,
                )

            resp.statusCode shouldBe HttpStatus.OK
            verify { grantConsent.execute(alumno, ConsentText.CURRENT_VERSION, "203.0.113.20", "chrome/1") }
        }

        test("grant mapea el error del caso de uso a la respuesta HTTP correspondiente") {
            val request = mockk<HttpServletRequest>(relaxed = true)
            every { grantConsent.execute(any(), any(), any(), any()) } returns IdentidadError.ConsentTextOutdated.left()

            val resp = controller.grant(MiConsentimientoRequest(versionConsentimiento = "vieja"), request)

            resp.statusCode shouldBe HttpStatus.CONFLICT
        }

        test("revoke delega en RevokeConsentCommand con el principal actual") {
            val consent =
                Consent(
                    id = ConsentId.new(),
                    userId = UserId.of(alumno.userId),
                    clubId = ClubId.of(alumno.clubId),
                    textVersion = ConsentText.CURRENT_VERSION,
                    grantedAt = Instant.now(),
                    revokedAt = Instant.now(),
                    ip = "203.0.113.10",
                    userAgent = "test-agent",
                )
            every { revokeConsent.execute(alumno) } returns consent.right()

            val resp = controller.revoke()

            resp.statusCode shouldBe HttpStatus.OK
            verify { revokeConsent.execute(alumno) }
        }
    })
