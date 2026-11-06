package com.runcriticon.identidad.application.usecases.club

import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import spock.lang.Specification

/**
 * Primera spec de Spock del backend. Además de cubrir el caso de uso, demuestra dos puntos del gate de ADR-0018 D8:
 * Groovy lee el bytecode de Java 25 (las clases Kotlin compilan ya a 25) y PITest ejecuta specs de Spock.
 */
class QueryClubQuerySpec extends Specification {

    ClubRepository repository = Mock()
    QueryClubQuery query = new QueryClubQuery(repository)

    def "falla con NotFound si el club del principal no existe"() {
        given:
        def actor = new Principal(UUID.randomUUID(), UUID.randomUUID(), Role.ENTRENADOR)

        when:
        def result = query.execute(actor)

        then: "consulta el repositorio una sola vez y no encuentra el club"
        1 * repository._ >> null
        0 * _

        and:
        result.leftOrNull() == IdentidadError.NotFound.INSTANCE
    }
}
