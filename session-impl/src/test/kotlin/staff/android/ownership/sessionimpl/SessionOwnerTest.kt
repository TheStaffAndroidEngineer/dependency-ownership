package staff.android.ownership.sessionimpl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.ownership.api.*
import staff.android.ownership.impl.createCart
import staff.android.ownership.session.*

class SessionOwnerTest {
    @Test fun sharedCurrentHandleSeesAcceptedEdit() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val saved = owner.current.value
        val legacy = owner.current.value
        assertSame(saved, legacy)
        assertInstanceOf(SessionEdit.Applied::class.java, saved.add("tea", 1))
        assertEquals(4, legacy.state.value.quantityOf("tea"))
        assertEquals(1, legacy.state.value.revision)
    }
    @Test fun maximumIsStillSharedPolicy() = runBlocking<Unit> {
        val session = createSessionOwner().current.value
        val before = session.state.value
        val edit = session.add("tea", 2) as SessionEdit.Rejected
        assertEquals(CartRejection.MAXIMUM_EXCEEDED, edit.reason)
        assertSame(before, session.state.value)
    }
    @Test fun staleHandleExplicitlyExpiresAndCannotMutateEitherCart() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val a = owner.current.value
        a.add("tea", 1)
        val b = owner.switchTo("B")
        assertInstanceOf(SessionEdit.Expired::class.java, a.setQuantity("tea", 0))
        assertEquals(4, a.state.value.quantityOf("tea"))
        assertEquals(1, b.state.value.quantityOf("tea"))
    }
    @Test fun staleInvalidCommandExpiresBeforeProductValidation() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val old = owner.current.value
        owner.switchTo("B")
        assertInstanceOf(SessionEdit.Expired::class.java, old.add("missing", -1))
    }
    @Test fun returningAccountIsNewGeneration() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val old = owner.current.value
        owner.switchTo("B")
        val again = owner.switchTo("A")
        assertEquals(SessionKey("A", 3), again.key)
        assertNotSame(old, again)
        assertEquals(3, again.state.value.quantityOf("tea"))
        assertInstanceOf(SessionEdit.Expired::class.java, old.add("tea", 1))
    }
    @Test fun selectingCurrentAccountIsIdempotent() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val before = owner.current.value
        assertSame(before, owner.switchTo("A"))
        assertEquals(1, owner.current.value.key.generation)
    }
    @Test fun failedReplacementLeavesOldAuthorityLive() = runBlocking<Unit> {
        val owner = createSessionOwner(cartFactory = { account ->
            check(account != "B") { "fixture factory failed" }
            fresh()
        })
        val old = owner.current.value
        assertThrows(IllegalStateException::class.java) { runBlocking { owner.switchTo("B") } }
        assertSame(old, owner.current.value)
        assertInstanceOf(SessionEdit.Applied::class.java, old.add("tea", 1))
    }
    @Test fun blankReplacementDoesNotRevokeCurrent() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val old = owner.current.value
        assertThrows(IllegalArgumentException::class.java) { runBlocking { owner.switchTo("") } }
        assertSame(old, owner.current.value)
        assertInstanceOf(SessionEdit.Applied::class.java, old.add("tea", 1))
    }
    @Test fun commandAlreadyInsideGateCompletesBeforeSwitch() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = createSessionOwner(cartFactory = { account ->
            val delegate = fresh(if (account == "A") 3 else 1)
            if (account == "A") object : CartRepository by delegate {
                override suspend fun add(productId: String, amount: Int): CartEdit {
                    entered.complete(Unit)
                    release.await()
                    return delegate.add(productId, amount)
                }
            } else delegate
        })
        val old = owner.current.value
        val edit = async(start = CoroutineStart.UNDISPATCHED) { old.add("tea", 1) }
        entered.await()
        val switching = async(start = CoroutineStart.UNDISPATCHED) { owner.switchTo("B") }
        assertFalse(switching.isCompleted)
        release.complete(Unit)
        assertInstanceOf(SessionEdit.Applied::class.java, edit.await())
        assertEquals(1, switching.await().state.value.quantityOf("tea"))
        assertInstanceOf(SessionEdit.Expired::class.java, old.add("tea", 1))
    }
    @Test fun cancelledCommandReleasesGateWithoutChangingCart() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val owner = createSessionOwner(cartFactory = { account ->
            val delegate = fresh(if (account == "A") 3 else 1)
            if (account == "A") object : CartRepository by delegate {
                override suspend fun add(productId: String, amount: Int): CartEdit {
                    entered.complete(Unit); never.await(); return delegate.add(productId, amount)
                }
            } else delegate
        })
        val old = owner.current.value
        val command = async { old.add("tea", 1) }
        entered.await(); command.cancel(); command.join()
        val b = owner.switchTo("B")
        assertEquals(3, old.state.value.quantityOf("tea"))
        assertEquals("B", b.key.accountId)
    }
    @Test fun concurrentAddKeepsMaximumAndOneRevision() = runBlocking<Unit> {
        val session = createSessionOwner().current.value
        val edits = (1..20).map { async { session.add("tea", 1) } }.awaitAll()
        assertEquals(1, edits.count { it is SessionEdit.Applied })
        assertEquals(19, edits.count { it is SessionEdit.Rejected })
        assertEquals(4, session.state.value.quantityOf("tea"))
        assertEquals(1, session.state.value.revision)
    }
    @Test fun zeroRemovesWithoutExposingMutableStorage() = runBlocking<Unit> {
        val session = createSessionOwner().current.value
        session.setQuantity("tea", 0)
        val snapshot = session.state.value
        assertEquals(0, snapshot.quantityOf("tea"))
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.lines as MutableList<CartItem>).add(CartItem("tea", 2))
        }
    }
    @Test fun previousSnapshotStaysHistorical() = runBlocking<Unit> {
        val session = createSessionOwner().current.value
        val previous = session.state.value
        session.add("tea", 1)
        assertEquals(3, previous.quantityOf("tea"))
        assertEquals(4, session.state.value.quantityOf("tea"))
    }
    @Test fun freshOwnerHasIndependentState() = runBlocking<Unit> {
        val first = createSessionOwner().current.value
        val second = createSessionOwner().current.value
        first.add("tea", 1)
        assertEquals(3, second.state.value.quantityOf("tea"))
    }
    @Test fun expiredSnapshotIsOriginalAccountsSnapshot() = runBlocking<Unit> {
        val owner = createSessionOwner()
        val a = owner.current.value
        val before = a.state.value
        owner.switchTo("B")
        val result = a.add("tea", 1)
        assertSame(before, result.snapshot)
        assertNotSame(result.snapshot, owner.current.value.state.value)
    }
    @Test fun noOpSetDoesNotAdvanceRevision() = runBlocking<Unit> {
        val session = createSessionOwner().current.value
        val before = session.state.value
        assertInstanceOf(SessionEdit.Applied::class.java, session.setQuantity("tea", 3))
        assertSame(before, session.state.value)
    }
    private fun fresh(quantity: Int = 3) = createCart(setOf("tea"), 4, listOf(CartItem("tea", quantity)))
}
