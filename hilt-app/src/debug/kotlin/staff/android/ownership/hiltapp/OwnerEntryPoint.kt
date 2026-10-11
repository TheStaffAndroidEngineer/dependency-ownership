package staff.android.ownership.hiltapp

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import staff.android.ownership.session.SessionOwner

// Debug-only diagnostic access: this joins the app graph processed by KSP.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OwnerEntryPoint { fun owner(): SessionOwner }
