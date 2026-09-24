package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.shared.autorizacion.spring.AuthScopeViolationException
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.PrincipalBuilder
import com.runcriticon.testing.TestClubs
import com.runcriticon.testing.TestPrincipalContext
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * `AuthScopeAspectIntegrationTest` solo ejercitaba `UserRepository.findById` (P0-5, auditoría de testing 2026-09):
 * el único repositorio de un módulo real (identidad) con `@AuthScope(Scope.CLUB)`. Este test ejercita el mismo
 * aspecto, pero real y tejido de verdad sobre otro módulo — `TaxonomyRepositoryImpl.findByClub` (`clubtaxonomia`) —
 * para confirmar que `AuthScopeEnforcementAspect` no es una verificación puntual de un solo repositorio, sino que
 * actúa sobre cualquier `@Repository` anotado, en cualquier módulo.
 */
class TaxonomyRepositoryAuthScopeIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var taxonomyRepository: TaxonomyRepositoryImpl

    @Test
    fun `findByClub con el clubId del principal no lanza`() {
        val club = TestClubs.newClub()
        TestPrincipalContext.withPrincipal(PrincipalBuilder().admin().inClub(club).build()) {
            taxonomyRepository.findByClub(club)
        }
    }

    @Test
    fun `findByClub con el clubId de otro club falla cerrado`() {
        val (clubPropio, clubAjeno) = TestClubs.twoClubs()
        TestPrincipalContext.withPrincipal(PrincipalBuilder().admin().inClub(clubPropio).build()) {
            shouldThrow<AuthScopeViolationException> {
                taxonomyRepository.findByClub(clubAjeno)
            }
        }
    }

    @Test
    fun `findByClub sin principal en el contexto falla cerrado`() {
        shouldThrow<AuthScopeViolationException> {
            taxonomyRepository.findByClub(ClubId.new())
        }
    }
}
