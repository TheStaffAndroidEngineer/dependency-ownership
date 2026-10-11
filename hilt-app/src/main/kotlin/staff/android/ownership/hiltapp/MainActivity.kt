package staff.android.ownership.hiltapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import staff.android.ownership.ui.OwnershipScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: HiltShopModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OwnershipScreen(model, "Hilt session owner") }
    }
}
