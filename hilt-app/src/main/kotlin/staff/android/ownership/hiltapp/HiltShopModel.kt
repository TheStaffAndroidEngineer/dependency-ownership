package staff.android.ownership.hiltapp

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import staff.android.ownership.session.SessionOwner
import staff.android.ownership.ui.ShopModel

@HiltViewModel
class HiltShopModel @Inject constructor(owner: SessionOwner) :
    ShopModel(owner)
