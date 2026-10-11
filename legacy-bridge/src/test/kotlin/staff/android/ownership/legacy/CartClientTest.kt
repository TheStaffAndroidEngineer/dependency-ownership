package staff.android.ownership.legacy

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.ownership.api.*
import staff.android.ownership.session.*

class CartClientTest {
    private class FakeSession(val operation: suspend () -> SessionEdit) : CartSession {
        override val key = SessionKey("A", 1)
        override val state = MutableStateFlow(CartSnapshot(listOf(CartItem("tea", 3)), 0))
        override suspend fun add(productId: String, amount: Int) = operation()
        override suspend fun setQuantity(productId: String, quantity: Int) = operation()
    }
    private class Recorder : CartCallback {
        val results = mutableListOf<SessionEdit>()
        val errors = mutableListOf<Exception>()
        override fun completed(result: SessionEdit) { results += result }
        override fun failed(error: Exception) { errors += error }
    }
    private fun applied() = SessionEdit.Applied(CartSnapshot(listOf(CartItem("tea", 4)), 1))

    @Test fun JavaFacingCurrentIsSnapshotNotFlow() {
        val session = FakeSession { applied() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        assertSame(session.state.value, createCartClient(session, scope).current())
        scope.cancel()
    }
    @Test fun successfulCommandDeliversExactlyOnce() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = Recorder()
        createCartClient(FakeSession { applied() }, scope).add("tea", 1, calls)
        assertEquals(1, calls.results.size); assertTrue(calls.errors.isEmpty())
        scope.cancel()
    }
    @Test fun operationalFailureUsesFailureCallback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failure = IllegalStateException("fixture")
        val calls = Recorder()
        createCartClient(FakeSession { throw failure }, scope).add("tea", 1, calls)
        assertSame(failure, calls.errors.single()); assertTrue(calls.results.isEmpty())
        scope.cancel()
    }
    @Test fun requestCancellationSuppressesSuspendedDelivery() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val calls = Recorder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val request = createCartClient(FakeSession { waiting.await(); applied() }, scope)
            .add("tea", 1, calls)
        request.cancel(); waiting.complete(Unit)
        assertTrue(calls.results.isEmpty()); assertTrue(calls.errors.isEmpty())
        scope.cancel()
    }
    @Test fun callerScopeCancellationSuppressesAllPendingCallbacks() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val calls = Recorder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val client = createCartClient(FakeSession { waiting.await(); applied() }, scope)
        repeat(3) { client.add("tea", 1, calls) }
        scope.cancel(); waiting.complete(Unit)
        assertTrue(calls.results.isEmpty()); assertTrue(calls.errors.isEmpty())
    }
    @Test fun cancellationAfterMutationDoesNotUndoMutation() = runBlocking {
        var quantity = 3
        val committed = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val calls = Recorder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val request = createCartClient(FakeSession {
            quantity = 4; committed.complete(Unit); never.await(); applied()
        }, scope).add("tea", 1, calls)
        committed.await(); request.cancel()
        assertEquals(4, quantity); assertTrue(calls.results.isEmpty())
        scope.cancel()
    }
    @Test fun expiredResultIsDeliveredWithoutPretendingSuccess() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = Recorder()
        val expired = SessionEdit.Expired(CartSnapshot(listOf(CartItem("tea", 3)), 0))
        createCartClient(FakeSession { expired }, scope).add("tea", 1, calls)
        assertSame(expired, calls.results.single()); assertTrue(calls.errors.isEmpty())
        scope.cancel()
    }
    @Test fun preservedBadgeConstructorKeepsOldBehavior() {
        assertEquals("Tea in Cart: 4", CartBadge(4).label())
        assertEquals("Coffee in Cart: 2", CartBadge(2, "Coffee").label())
    }
}
