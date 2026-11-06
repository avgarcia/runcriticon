package com.runcriticon.identidad.application.usecases.club

import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/**
 * Gate de ADR-0018 D8: `spock-spring` funciona sobre el TestContext de Spring Framework 7 y `@SpringBean` sustituye
 * un bean del contexto por un mock de Spock. Sirve de plantilla para las specs con contexto Spring; se retira cuando
 * la primera spec real de ese tipo cubra lo mismo.
 */
@ContextConfiguration(classes = QueryClubQuery)
class QueryClubQuerySpringSpec extends Specification {

    @SpringBean
    ClubRepository repository = Mock()

    @Autowired
    QueryClubQuery query

    def "el caso de uso inyectado por Spring usa el mock declarado con @SpringBean"() {
        when:
        def result = query.execute(new Principal(UUID.randomUUID(), UUID.randomUUID(), Role.ALUMNO))

        then:
        1 * repository._ >> null

        and:
        result.leftOrNull() == IdentidadError.NotFound.INSTANCE
    }
}
