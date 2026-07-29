package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.api.rest.ErrorResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

/**
 * Cada variante de [ClubTaxonomiaError] con su status y su `code` granular.
 *
 * Incluye las dos ramas `else` defensivas: `reason` es un `String` libre, así que un valor nuevo sin traducir debe
 * degradar al código genérico en vez de tumbar la petición. Son las que impiden que una razón nueva se cuele.
 */
class TaxonomyErrorMappingTest :
    FunSpec({
        data class Caso(
            val error: ClubTaxonomiaError,
            val status: HttpStatus,
            val code: String,
            val field: String?,
        )

        val casos =
            listOf(
                Caso(ClubTaxonomiaError.Forbidden, HttpStatus.FORBIDDEN, "FORBIDDEN", null),
                Caso(
                    ClubTaxonomiaError.InvalidInput("nombre", "blank"),
                    HttpStatus.BAD_REQUEST,
                    "LABEL_BLANK",
                    "nombre",
                ),
                Caso(
                    ClubTaxonomiaError.InvalidInput("valor", "too_long"),
                    HttpStatus.BAD_REQUEST,
                    "LABEL_TOO_LONG",
                    "valor",
                ),
                Caso(
                    ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel"),
                    HttpStatus.CONFLICT,
                    "DUPLICATE_LABEL",
                    "nombre",
                ),
                Caso(ClubTaxonomiaError.TagKeyNotFound, HttpStatus.NOT_FOUND, "TAG_KEY_NOT_FOUND", null),
                Caso(ClubTaxonomiaError.TagValueNotFound, HttpStatus.NOT_FOUND, "TAG_VALUE_NOT_FOUND", null),
                Caso(
                    ClubTaxonomiaError.Conflict("tag_key_archived"),
                    HttpStatus.CONFLICT,
                    "TAG_KEY_ARCHIVED",
                    null,
                ),
                // Ramas else: razones que el mapeador no traduce explícitamente.
                Caso(ClubTaxonomiaError.Conflict("duplicate_id"), HttpStatus.CONFLICT, "CONFLICT", null),
                Caso(
                    ClubTaxonomiaError.InvalidInput("nombre", "razon_futura_sin_traducir"),
                    HttpStatus.BAD_REQUEST,
                    "INVALID_INPUT",
                    "nombre",
                ),
            )

        casos.forEach { caso ->
            test("${caso.error::class.simpleName} → ${caso.status.value()} ${caso.code}") {
                val resp = caso.error.toErrorResponse()

                resp.statusCode shouldBe caso.status
                resp.body!!.code shouldBe caso.code
                resp.body!!.field shouldBe caso.field
            }
        }

        test("el reason del dominio nunca se filtra al message") {
            val resp: org.springframework.http.ResponseEntity<ErrorResponse> =
                ClubTaxonomiaError.InvalidInput("nombre", "too_long").toErrorResponse()

            resp.body!!.message shouldBe "El nombre supera la longitud máxima"
        }
    })
