package staff.android.dependencies.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

abstract class LeasePlanContract {
    abstract fun plan(state: LeaseState, event: LeaseEvent): LeaseTransition

    @ParameterizedTest(name = "{0}")
    @MethodSource("histories")
    fun returnsExactNextStateAndEffects(
        label: String,
        before: LeaseState,
        event: LeaseEvent,
        expected: LeaseTransition,
    ) {
        assertEquals(expected, plan(before, event), label)
    }

    @Test fun twoBorrowersCloseOnlyAfterBothRelease() {
        var state = empty()
        val effects = mutableListOf<ClientEffect>()
        fun apply(event: LeaseEvent) {
            val result = plan(state, event)
            state = result.state
            effects += result.effects
        }
        apply(LeaseEvent.OpenScope("north-1"))
        apply(LeaseEvent.Acquire("north-1", "preview-1", "client-1"))
        apply(LeaseEvent.Acquire("north-1", "export-1", "unused"))
        assertEquals(
            mapOf("north-1" to client("client-1", "preview-1", "export-1")),
            state.clients,
        )
        apply(LeaseEvent.Release("preview-1"))
        assertEquals(
            listOf(ClientEffect.Create("north-1", "client-1")), effects,
            "preview release must not close export's client",
        )
        apply(LeaseEvent.Release("export-1"))
        assertEquals(LeaseState(setOf("north-1"), emptyMap()), state)
        assertEquals(listOf(
            ClientEffect.Create("north-1", "client-1"),
            ClientEffect.Close("north-1", "client-1"),
        ), effects)
        apply(LeaseEvent.Release("export-1"))
        assertEquals(2, effects.size, "repeated release adds no second close")
    }

    @Test fun revokedBorrowerCannotCloseReplacementOpening() {
        var state = leased("north-1", "client-1", "preview-1", "export-1")
        val revoked = plan(state, LeaseEvent.RevokeScope("north-1"))
        assertEquals(empty(), revoked.state)
        assertEquals(listOf(ClientEffect.Close("north-1", "client-1")), revoked.effects)
        state = plan(revoked.state, LeaseEvent.OpenScope("north-2")).state
        val replacement = plan(state, LeaseEvent.Acquire("north-2", "preview-2", "client-2"))
        assertEquals(listOf(ClientEffect.Create("north-2", "client-2")), replacement.effects)
        val late = plan(replacement.state, LeaseEvent.Release("preview-1"))
        assertEquals(LeaseTransition(replacement.state, emptyList()), late)
        val repeated = plan(late.state, LeaseEvent.RevokeScope("north-1"))
        assertEquals(late, repeated)
    }

    @Test fun sameOpenScopeCanCreateAnotherClientAfterItsLastRelease() {
        val first = leased("north-1", "client-1", "preview-1")
        val released = plan(first, LeaseEvent.Release("preview-1"))
        assertEquals(listOf(ClientEffect.Close("north-1", "client-1")), released.effects)
        val next = plan(released.state, LeaseEvent.Acquire("north-1", "preview-2", "client-2"))
        assertEquals(leased("north-1", "client-2", "preview-2"), next.state)
        assertEquals(listOf(ClientEffect.Create("north-1", "client-2")), next.effects)
        assertEquals(LeaseTransition(next.state, emptyList()),
            plan(next.state, LeaseEvent.Release("preview-1")))
    }

    @Test fun sharingIgnoresTheUnusedClientCandidate() {
        val state = LeaseState(setOf("north", "south"), mapOf(
            "north" to client("client-n", "preview-n"),
            "south" to client("client-s", "preview-s"),
        ))
        val expected = LeaseTransition(state.copy(clients = state.clients +
            ("north" to client("client-n", "preview-n", "export-n"))), emptyList())
        for (ignored in listOf("", " ", "client-s", "different")) {
            assertEquals(expected, plan(state,
                LeaseEvent.Acquire("north", "export-n", ignored)))
        }
    }

    @Test fun doesNotMutateInputsOrNormalizeOpaqueIds() {
        val scopes = linkedSetOf(" north ", "south")
        val leases = linkedSetOf(" preview ")
        val clients = linkedMapOf(" north " to ClientLease(" client ", leases))
        val state = LeaseState(scopes, clients)
        val result = plan(state, LeaseEvent.Acquire(" north ", " export ", ""))
        assertEquals(LeaseState(setOf(" north ", "south"), mapOf(
            " north " to client(" client ", " preview ", " export "),
        )), result.state)
        assertEquals(setOf(" north ", "south"), scopes)
        assertEquals(setOf(" preview "), leases)
        assertEquals(mapOf(" north " to client(" client ", " preview ")), clients)
    }

    @Test fun rejectsInvalidRegistryBeforeEvenAnUnknownRelease() {
        val invalid = listOf(
            LeaseState(setOf(" "), emptyMap()),
            LeaseState(emptySet(), mapOf("north" to client("c", "l"))),
            leased("north", " ", "l"),
            leased("north", "c"),
            leased("north", "c", " "),
            LeaseState(setOf("north", "south"), mapOf(
                "north" to client("c", "l1"), "south" to client("c", "l2"),
            )),
            LeaseState(setOf("north", "south"), mapOf(
                "north" to client("c1", "same"), "south" to client("c2", "same"),
            )),
        )
        for (state in invalid) assertThrows(IllegalArgumentException::class.java) {
            plan(state, LeaseEvent.Release("missing"))
        }
    }

    @Test fun rejectsInvalidEventsWithoutChangingTheRegistry() {
        val state = LeaseState(setOf("north", "south"), mapOf(
            "north" to client("client-1", "preview-1"),
        ))
        val events = listOf(
            LeaseEvent.OpenScope(""), LeaseEvent.OpenScope("north"),
            LeaseEvent.Acquire("closed", "new", "client-2"),
            LeaseEvent.Acquire("north", " ", "unused"),
            LeaseEvent.Acquire("south", "preview-1", "client-2"),
            LeaseEvent.Acquire("south", "export-1", " "),
            LeaseEvent.Acquire("south", "export-1", "client-1"),
            LeaseEvent.Release(""), LeaseEvent.RevokeScope(" "),
        )
        for (event in events) assertThrows(IllegalArgumentException::class.java) {
            plan(state, event)
        }
        assertEquals(mapOf("north" to client("client-1", "preview-1")), state.clients)
    }

    companion object {
        private fun empty() = LeaseState(emptySet(), emptyMap())
        private fun client(id: String, vararg leases: String) = ClientLease(id, leases.toSet())
        private fun leased(scope: String, id: String, vararg leases: String) =
            LeaseState(setOf(scope), mapOf(scope to client(id, *leases)))
        private fun transition(state: LeaseState, vararg effects: ClientEffect) =
            LeaseTransition(state, effects.toList())

        @JvmStatic fun histories(): List<Arguments> {
            val north = leased("north", "client-n", "preview")
            val shared = leased("north", "client-n", "preview", "export")
            val both = LeaseState(setOf("north", "south"), mapOf(
                "north" to client("client-n", "preview", "export"),
                "south" to client("client-s", "other"),
            ))
            return listOf(
                Arguments.of("open creates no client", empty(), LeaseEvent.OpenScope("north"),
                    transition(LeaseState(setOf("north"), emptyMap()))),
                Arguments.of("first borrower creates once", LeaseState(setOf("north"), emptyMap()),
                    LeaseEvent.Acquire("north", "preview", "client-n"),
                    transition(north, ClientEffect.Create("north", "client-n"))),
                Arguments.of("second borrower shares", north,
                    LeaseEvent.Acquire("north", "export", "unused"), transition(shared)),
                Arguments.of("one release preserves other borrower", shared,
                    LeaseEvent.Release("preview"), transition(leased("north", "client-n", "export"))),
                Arguments.of("last release closes but keeps scope", north,
                    LeaseEvent.Release("preview"), transition(LeaseState(setOf("north"), emptyMap()),
                        ClientEffect.Close("north", "client-n"))),
                Arguments.of("unknown release is harmless", north,
                    LeaseEvent.Release("old"), transition(north)),
                Arguments.of("revoke closes once despite two borrowers", shared,
                    LeaseEvent.RevokeScope("north"), transition(empty(),
                        ClientEffect.Close("north", "client-n"))),
                Arguments.of("empty scope revocation emits no close", LeaseState(setOf("north"), emptyMap()),
                    LeaseEvent.RevokeScope("north"), transition(empty())),
                Arguments.of("unknown revocation is harmless", north,
                    LeaseEvent.RevokeScope("old"), transition(north)),
                Arguments.of("release changes only owning scope", both,
                    LeaseEvent.Release("preview"), transition(both.copy(clients = both.clients +
                        ("north" to client("client-n", "export"))))),
                Arguments.of("revocation leaves other scope live", both,
                    LeaseEvent.RevokeScope("north"), transition(leased("south", "client-s", "other"),
                        ClientEffect.Close("north", "client-n"))),
                Arguments.of("new independent scope preserves existing client", north,
                    LeaseEvent.OpenScope("south"), transition(north.copy(activeScopes = setOf("north", "south")))),
            )
        }
    }
}

class LeasePlanReferenceTest : LeasePlanContract() {
    override fun plan(state: LeaseState, event: LeaseEvent) =
        staff.android.dependencies.transfer.solution.planLease(state, event)
}

@Tag("exercise")
class LeasePlanExerciseTest : LeasePlanContract() {
    override fun plan(state: LeaseState, event: LeaseEvent) =
        staff.android.dependencies.transfer.exercise.planLease(state, event)
}
