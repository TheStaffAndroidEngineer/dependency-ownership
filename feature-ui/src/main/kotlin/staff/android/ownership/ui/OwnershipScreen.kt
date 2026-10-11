package staff.android.ownership.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import staff.android.ownership.legacy.createCartClient
import staff.android.ownership.session.CartSession

@Composable
fun OwnershipScreen(model: ShopModel, wiring: String) {
    val cart by model.cart.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val cartState by cart.state.collectAsStateWithLifecycle()
    val savedState by saved.state.collectAsStateWithLifecycle()
    val result by model.result.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf("saved") }
    CommonGoodsTheme {
      Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommonGoodsHeader()
            Text("Account ${cart.key.accountId}", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { destination = "saved" }) {
                Text("Open Saved items")
            }
            Button(onClick = { destination = "cart" }) {
                Text("Open legacy Cart")
            }
            if (destination == "saved") {
                SavedPanel(
                    saved,
                    model::addFromSaved,
                    onOpenCart = { destination = "cart" },
                )
            } else {
                key(cart.key) { LegacyCartPanel(cart) }
            }
            if (result.isNotBlank()) ShopMessage(result)
            LabControls {
            Text(wiring)
            Text("Account ${cart.key.accountId}; generation ${cart.key.generation}")
            Text("Cart Tea: ${cartState.quantityOf("tea")}")
            Text("Saved sees: ${savedState.quantityOf("tea")}")
            Text("In-memory fixture. A seeds 3; B seeds 1. New process resets A.")
            Button(onClick = { model.switchAccount("B") }) {
                Text("Switch to B")
            }
            Button(onClick = { model.switchAccount("A") }) {
                Text("Switch to A")
            }
            Button(onClick = model::replayHeldA) {
                Text("Try held A handle")
            }
            }
        }
      }
    }
}

@Composable
fun SavedPanel(
    session: CartSession,
    onAdd: (CartSession) -> Unit,
    onOpenCart: () -> Unit,
) {
    Text("Saved items: Tea", style = MaterialTheme.typography.headlineMedium)
    ProductIdentity("tea", "Tea")
    Button(onClick = { onAdd(session) }) {
        Text("Add 1 Tea from Saved")
    }
    Button(onClick = onOpenCart) { Text("Review Cart") }
}

@Composable
private fun LegacyCartPanel(session: CartSession) {
    val scope = rememberCoroutineScope()
    val client =
        remember(session, scope) { createCartClient(session, scope) }
    val snapshot by session.state.collectAsStateWithLifecycle()
    ProductIdentity("tea", "Tea", "In your cart")
    AndroidView(
        factory = { LegacyCartView(it) },
        onRelease = { it.release() },
        update = { it.render(client, snapshot.quantityOf("tea")) },
        modifier = Modifier.testTag("legacy-cart"),
    )
}
