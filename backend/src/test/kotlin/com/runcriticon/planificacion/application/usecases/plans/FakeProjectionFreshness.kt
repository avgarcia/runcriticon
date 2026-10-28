package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.MembersProjectionLag
import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.planificacion.domain.GroupId

/**
 * Doble de [ProjectionFreshness] con un lag fijo, para probar la puerta fail-closed sin Postgres real. Con
 * [onlyForGroup], el lag solo existe para ese grupo y el resto está al día — para probar que un retraso ajeno no
 * bloquea. [requestedGroups] anota por qué grupo se preguntó.
 */
class FakeProjectionFreshness(
    private val lagSeconds: Long = 0L,
    private val failed: Boolean = false,
    private val onlyForGroup: GroupId? = null,
) : ProjectionFreshness {
    val requestedGroups: MutableList<GroupId> = mutableListOf()

    override fun membersProjectionLag(groupId: GroupId): MembersProjectionLag {
        requestedGroups += groupId
        return if (onlyForGroup == null || onlyForGroup == groupId) {
            MembersProjectionLag(lagSeconds, failed)
        } else {
            MembersProjectionLag(0L, failed = false)
        }
    }
}
