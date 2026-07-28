package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.clubtaxonomia.domain.taxonomy.TaxonomyUpdate
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId

/**
 * Fontanería común de los seis comandos que mutan la taxonomía: cargar el agregado del club del actor, aplicarle
 * [mutation] y persistir el resultado, devolviendo el elemento concreto que cambió.
 *
 * **Es una función y no una clase base con Template Method a propósito.** `AuthorizationArchTest` exige que cada
 * clase `@ApplicationService` acceda a `AuthorizationMatrix` en su propio bytecode; un guard heredado de una
 * superclase no lo cumpliría y —lo que importa más— dejaría de verse al leer el caso de uso, que es justo lo que
 * persigue la regla. La comprobación de la matriz se queda por tanto escrita en cada comando.
 *
 * Lo que sí centraliza es el `club_id`: sale siempre del principal y nunca de un parámetro del cliente, y es el mismo
 * en la carga y en el guardado. Repetir ese emparejamiento seis veces es donde cabría un desliz de copia y pega.
 */
internal fun <T> TaxonomyRepository.mutate(
    actor: Principal,
    mutation: (Taxonomy) -> Either<ClubTaxonomiaError, TaxonomyUpdate<T>>,
): Either<ClubTaxonomiaError, T> =
    either {
        val clubId = ClubId.of(actor.clubId)
        val update = mutation(findByClub(clubId)).bind()
        save(clubId, update.taxonomy)
        update.changed
    }
