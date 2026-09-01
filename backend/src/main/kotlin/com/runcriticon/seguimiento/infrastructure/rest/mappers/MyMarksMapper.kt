package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.api.rest.MiMarcaResponse
import com.runcriticon.shared.api.rest.MisMarcasResponse
import java.time.ZoneOffset

/** Las cuatro marcas, en el orden en que ya llegan de [com.runcriticon.seguimiento.application.usecases.marks.GetMyMarksQuery]
 * (5K, 10K, 21K, 42K) — sin reordenar aquí. */
internal fun Map<RaceDistance, StudentMark?>.toResponse(): MisMarcasResponse =
    MisMarcasResponse(marcas = map { (distance, mark) -> distance.toResponse(mark) })

/** La marca recién guardada, para la respuesta de `PUT /me/marcas/{distancia}`. */
internal fun StudentMark.toResponse(): MiMarcaResponse = distance.toResponse(this)

private fun RaceDistance.toResponse(mark: StudentMark?): MiMarcaResponse =
    MiMarcaResponse(
        distancia = toMiMarcaResponseDistancia(),
        tiempoSegundos = mark?.timeSeconds,
        modificadoEn = mark?.modifiedAt?.atOffset(ZoneOffset.UTC),
    )

private fun RaceDistance.toMiMarcaResponseDistancia(): MiMarcaResponse.Distancia =
    when (this) {
        RaceDistance.FIVE_K -> MiMarcaResponse.Distancia._5_K
        RaceDistance.TEN_K -> MiMarcaResponse.Distancia._10_K
        RaceDistance.HALF_MARATHON -> MiMarcaResponse.Distancia._21_K
        RaceDistance.MARATHON -> MiMarcaResponse.Distancia._42_K
    }

/** `distancia` de la ruta (`/me/marcas/{distancia}`) a tipo de dominio. Sin generado: el controller de este
 * módulo no implementa una interfaz generada (mismo criterio que `dia` en `MyPlanController`), así que el
 * puente vive aquí, a mano — `null` si el segmento no es una de las cuatro distancias estándar. */
internal fun String.toRaceDistanceOrNull(): RaceDistance? =
    when (this) {
        "5K" -> RaceDistance.FIVE_K
        "10K" -> RaceDistance.TEN_K
        "21K" -> RaceDistance.HALF_MARATHON
        "42K" -> RaceDistance.MARATHON
        else -> null
    }
