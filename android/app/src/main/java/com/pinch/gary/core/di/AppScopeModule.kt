package com.pinch.gary.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * One process-lifetime [CoroutineScope] that [core.appstate.AppState] uses to
 * `stateIn()` the combined app state, and that feature managers use for
 * work that must outlive any single screen's ViewModel.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @ApplicationScope
    @Singleton
    @Provides
    fun providesApplicationScope(
        @DefaultDispatcher defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
