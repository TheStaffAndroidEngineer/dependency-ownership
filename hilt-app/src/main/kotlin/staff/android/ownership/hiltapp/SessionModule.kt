package staff.android.ownership.hiltapp

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import staff.android.ownership.session.SessionOwner
import staff.android.ownership.sessionimpl.createSessionOwner

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    fun owner(): SessionOwner = createSessionOwner()
}
