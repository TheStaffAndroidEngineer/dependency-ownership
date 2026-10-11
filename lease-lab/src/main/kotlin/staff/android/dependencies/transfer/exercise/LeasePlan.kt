package staff.android.dependencies.transfer.exercise

import staff.android.dependencies.transfer.*

// Deliberately incomplete: repair sharing, release and revocation.
fun planLease(state: LeaseState, event: LeaseEvent): LeaseTransition {
    validateLeaseInput(state, event)
    return when (event) {
        is LeaseEvent.OpenScope ->
            LeaseTransition(
                state.copy(
                    activeScopes = state.activeScopes + event.scopeId
                ),
                emptyList(),
            )
        is LeaseEvent.Acquire ->
            LeaseTransition(
                state.copy(
                    clients =
                        state.clients +
                            (event.scopeId to
                                ClientLease(
                                    event.freshClientId,
                                    setOf(event.leaseId),
                                ))
                ),
                listOf(
                    ClientEffect.Create(
                        event.scopeId,
                        event.freshClientId,
                    )
                ),
            )
        is LeaseEvent.Release -> {
            val entry =
                state.clients.entries.firstOrNull {
                    event.leaseId in it.value.leaseIds
                }
            if (entry == null) LeaseTransition(state, emptyList())
            else
                LeaseTransition(
                    state.copy(clients = state.clients - entry.key),
                    listOf(
                        ClientEffect.Close(
                            entry.key,
                            entry.value.clientId,
                        )
                    ),
                )
        }
        is LeaseEvent.RevokeScope -> LeaseTransition(state, emptyList())
    }
}
