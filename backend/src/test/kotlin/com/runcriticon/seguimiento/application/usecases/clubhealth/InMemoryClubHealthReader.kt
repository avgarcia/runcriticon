package com.runcriticon.seguimiento.application.usecases.clubhealth

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ClubHealthReader
import com.runcriticon.seguimiento.domain.GroupActivity
import com.runcriticon.shared.tenancy.ClubId

/** Registro de una llamada a [ClubHealthReader.findLastActivityByGroup]. */
data class ClubHealthReaderCall(
    val clubId: ClubId,
)

/**
 * Doble en memoria del puerto. Además de devolver lo configurado, registra con qué se le llamó — es lo que
 * hay que comprobar en el caso de uso, no el SQL de agregación (ya cubierto contra Postgres real).
 */
class InMemoryClubHealthReader(
    private val activity: List<GroupActivity> = emptyList(),
) : ClubHealthReader {
    val calls: MutableList<ClubHealthReaderCall> = mutableListOf()

    override fun findLastActivityByGroup(clubId: ClubId): List<GroupActivity> {
        calls += ClubHealthReaderCall(clubId)
        return activity
    }
}
