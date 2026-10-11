package staff.android.dependencies.transfer

data class ClientLease(
    val clientId: String,
    val leaseIds: Set<String>,
)

data class LeaseState(
    val activeScopes: Set<String>,
    val clients: Map<String, ClientLease>,
)

sealed interface LeaseEvent {
    data class OpenScope(val scopeId: String) : LeaseEvent

    data class Acquire(
        val scopeId: String,
        val leaseId: String,
        val freshClientId: String,
    ) : LeaseEvent

    data class Release(val leaseId: String) : LeaseEvent

    data class RevokeScope(val scopeId: String) : LeaseEvent
}

sealed interface ClientEffect {
    data class Create(
        val scopeId: String,
        val clientId: String,
    ) : ClientEffect

    data class Close(
        val scopeId: String,
        val clientId: String,
    ) : ClientEffect
}

data class LeaseTransition(
    val state: LeaseState,
    val effects: List<ClientEffect>,
)

fun validateLeaseInput(state: LeaseState, event: LeaseEvent) {
    require(state.activeScopes.all { it.isNotBlank() })
    require(state.clients.keys.all { it in state.activeScopes })
    val clientIds = mutableSetOf<String>()
    val leaseIds = mutableSetOf<String>()
    for (client in state.clients.values) {
        require(client.clientId.isNotBlank())
        require(clientIds.add(client.clientId))
        require(client.leaseIds.isNotEmpty())
        for (leaseId in client.leaseIds) {
            require(leaseId.isNotBlank())
            require(leaseIds.add(leaseId))
        }
    }
    when (event) {
        is LeaseEvent.OpenScope -> {
            require(event.scopeId.isNotBlank())
            require(event.scopeId !in state.activeScopes)
        }
        is LeaseEvent.Acquire -> {
            require(event.scopeId in state.activeScopes)
            require(event.leaseId.isNotBlank())
            require(event.leaseId !in leaseIds)
            if (event.scopeId !in state.clients) {
                require(event.freshClientId.isNotBlank())
                require(event.freshClientId !in clientIds)
            }
        }
        is LeaseEvent.Release -> require(event.leaseId.isNotBlank())
        is LeaseEvent.RevokeScope -> require(event.scopeId.isNotBlank())
    }
}
