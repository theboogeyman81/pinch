package com.pinch.gary.glasses

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GlassesModule {

    @Binds
    @Singleton
    abstract fun bindsGlassesManager(impl: GlassesManagerImpl): GlassesManager
}
