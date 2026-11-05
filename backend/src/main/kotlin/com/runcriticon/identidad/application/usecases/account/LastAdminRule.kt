package com.runcriticon.identidad.application.usecases.account

import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role

private val STATUSES_ABLE_TO_ENTER: Set<UserStatus> = UserStatus.entries.filter { it.canEnter() }.toSet()

/**
 * Un club sin ningún administrador capaz de entrar queda huérfano: nadie podría volver a invitar ni gestionar nada.
 * Cuentan los administradores `ACTIVO` e `INVITADO` —este último porque aún puede activar su cuenta y recuperar el
 * club—; uno `DESACTIVADO` o en `BAJA` no, porque no puede iniciar sesión.
 *
 * Solo aplica si [target] es uno de los que cuentan: suprimir o dar de baja a un admin que ya no puede entrar no cambia
 * cuántos quedan. Si [target] es el único que queda, la operación se rechaza.
 */
internal fun UserRepository.isLastAdminAbleToEnter(target: User): Boolean =
    target.role == Role.ADMIN &&
        target.status.canEnter() &&
        countByRoleAndStatuses(target.clubId, Role.ADMIN, STATUSES_ABLE_TO_ENTER) <= 1
