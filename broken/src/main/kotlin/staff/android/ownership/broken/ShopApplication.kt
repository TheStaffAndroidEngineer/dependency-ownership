package staff.android.ownership.broken

import android.app.Application
import staff.android.ownership.sessionimpl.createSessionOwner

class AppGraph {
    val sessions = createSessionOwner()
    val savedSessions =
        createSessionOwner() // Deliberate defect: a second authority.
}

class ShopApplication : Application() {
    val graph by lazy { AppGraph() }
}
