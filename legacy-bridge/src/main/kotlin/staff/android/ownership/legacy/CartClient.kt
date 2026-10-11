package staff.android.ownership.legacy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import staff.android.ownership.api.CartSnapshot
import staff.android.ownership.session.CartSession
import staff.android.ownership.session.SessionEdit

interface CartCallback {
    fun completed(result: SessionEdit)

    fun failed(error: Exception)
}

fun interface CancelRequest {
    fun cancel()
}

interface CartClient {
    fun current(): CartSnapshot

    fun add(
        productId: String,
        amount: Int,
        callback: CartCallback,
    ): CancelRequest
}

// The caller supplies its own scope; the session owner never stores
// callbacks.
fun createCartClient(
    session: CartSession,
    scope: CoroutineScope,
): CartClient = ScopedCartClient(session, scope)

private class ScopedCartClient(
    private val session: CartSession,
    private val scope: CoroutineScope,
) : CartClient {
    override fun current(): CartSnapshot = session.state.value

    override fun add(
        productId: String,
        amount: Int,
        callback: CartCallback,
    ): CancelRequest {
        val job = scope.launch {
            val result =
                try {
                    session.add(productId, amount)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    callback.failed(error)
                    return@launch
                }
            currentCoroutineContext().ensureActive()
            callback.completed(result)
        }
        return CancelRequest { job.cancel() }
    }
}
