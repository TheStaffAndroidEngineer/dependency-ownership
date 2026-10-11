package staff.android.ownership

import android.app.Application
import staff.android.ownership.sessionimpl.createSessionOwner

class AppGraph {
    val sessions = createSessionOwner()
}

class ShopApplication : Application() {
    val graph by lazy { AppGraph() }
}
