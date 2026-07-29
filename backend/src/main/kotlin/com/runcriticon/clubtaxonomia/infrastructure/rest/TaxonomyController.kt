package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ListTaxonomyQuery
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Lectura de la taxonomía del club. Va aparte de los controllers de escritura porque es el único endpoint con
 * `TAXONOMY:LIST` —lo consultan ADMIN y ENTRENADOR—; todo lo demás es `TAXONOMY:MANAGE`, solo ADMIN.
 */
@RestController
@RequestMapping("/api/taxonomia")
class TaxonomyController(
    private val listTaxonomy: ListTaxonomyQuery,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/taxonomia — la taxonomía completa del club, con los archivados incluidos. */
    @GetMapping
    @Authorize("TAXONOMY:LIST")
    fun get(): ResponseEntity<*> =
        listTaxonomy.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { taxonomy -> ResponseEntity.ok(taxonomy.toResponse()) },
        )
}
