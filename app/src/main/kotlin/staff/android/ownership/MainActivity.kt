package staff.android.ownership

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import staff.android.ownership.ui.OwnershipScreen
import staff.android.ownership.ui.ShopModel

class MainActivity : ComponentActivity() {
    private val model: ShopModel by viewModels {
        val graph = (application as ShopApplication).graph
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                require(modelClass == ShopModel::class.java)
                return ShopModel(graph.sessions) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OwnershipScreen(model, "Manual session owner") }
    }
}
