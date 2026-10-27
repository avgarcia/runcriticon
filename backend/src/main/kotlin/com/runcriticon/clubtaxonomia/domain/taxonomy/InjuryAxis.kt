package com.runcriticon.clubtaxonomia.domain.taxonomy

import com.runcriticon.clubtaxonomia.domain.tag.TagValueId

/**
 * El eje `estado` del club y su valor `lesión`: el tag de la taxonomía que lleva un dato de salud (art. 9 RGPD).
 * Lo asigna `LesionDeclaradaListener` cuando el alumno avisa de lesión desde el reajuste de día, o un entrenador a
 * mano. Única definición para todo lo que necesita reconocerlo: quien lo asigna y quien audita su lectura.
 *
 * Se localiza **por nombre normalizado** (sin tildes ni mayúsculas): la taxonomía no tiene un mecanismo de "tag
 * reservado del sistema" que lo identifique por id. Consecuencia que hay que conocer: si un admin renombra el eje o
 * el valor, [injuryAxis] devuelve `null` y todo lo que depende de él deja de actuar **sin avisar** — el listener deja
 * de marcar la lesión (falla cerrado: no escribe), pero la lectura deja de auditarse (falla abierto).
 *
 * @property axisValueIds todos los valores del eje `estado`, que es conceptualmente exclusivo.
 * @property injuryValueId el valor `lesión`.
 */
data class InjuryAxis(
    val axisValueIds: Set<TagValueId>,
    val injuryValueId: TagValueId,
)

/** Ver [InjuryAxis]. `null` si el club no tiene un eje `estado` con un valor `lesión`. */
fun Taxonomy.injuryAxis(): InjuryAxis? =
    keys
        .firstOrNull { it.label.normalized == "estado" }
        ?.let { estadoKey ->
            estadoKey.values
                .firstOrNull { it.label.normalized == "lesion" }
                ?.let { injury -> InjuryAxis(estadoKey.values.mapTo(mutableSetOf()) { it.id }, injury.id) }
        }
