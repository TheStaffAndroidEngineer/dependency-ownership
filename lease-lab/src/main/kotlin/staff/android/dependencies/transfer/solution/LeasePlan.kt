package staff.android.dependencies.transfer.solution

import staff.android.dependencies.transfer.*

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
        is LeaseEvent.Acquire -> {
            val existing = state.clients[event.scopeId]
            val client =
                if (existing == null) {
                    ClientLease(
                        event.freshClientId,
                        setOf(event.leaseId),
                    )
                } else {
                    existing.copy(
                        leaseIds = existing.leaseIds + event.leaseId
                    )
                }
            val effects =
                if (existing == null) {
                    listOf(
                        ClientEffect.Create(
                            event.scopeId,
                            client.clientId,
                        )
                    )
                } else emptyList()
            LeaseTransition(
                state.copy(
                    clients = state.clients + (event.scopeId to client)
                ),
                effects,
            )
        }
        is LeaseEvent.Release -> {
            val entry =
                state.clients.entries.firstOrNull {
                    event.leaseId in it.value.leaseIds
                }
            if (entry == null) LeaseTransition(state, emptyList())
            else {
                val remaining = entry.value.leaseIds - event.leaseId
                if (remaining.isEmpty())
                    LeaseTransition(
                        state.copy(clients = state.clients - entry.key),
                        listOf(
                            ClientEffect.Close(
                                entry.key,
                                entry.value.clientId,
                            )
                        ),
                    )
                else
                    LeaseTransition(
                        state.copy(
                            clients =
                                state.clients +
                                    (entry.key to
                                        entry.value.copy(
                                            leaseIds = remaining
                                        ))
                        ),
                        emptyList(),
                    )
            }
        }
        is LeaseEvent.RevokeScope -> {
            val existing = state.clients[event.scopeId]
            LeaseTransition(
                LeaseState(
                    state.activeScopes - event.scopeId,
                    state.clients - event.scopeId,
                ),
                if (existing == null) emptyList()
                else
                    listOf(
                        ClientEffect.Close(
                            event.scopeId,
                            existing.clientId,
                        )
                    ),
            )
        }
    }
}
