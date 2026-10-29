package com.runcriticon.testing

import com.runcriticon.shared.observability.UserIdHasher
import java.util.UUID

/** Doble de [UserIdHasher] con un hash fijo, para afirmar el `user_id_hash` que un listener deja en el MDC. */
object ConstantUserIdHasher : UserIdHasher {
    const val HASH = "hash-del-actor"

    override fun hash(userId: UUID): String = HASH
}
