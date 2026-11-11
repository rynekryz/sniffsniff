package com.rynekryz.sniffsniff.core.common.di

import com.rynekryz.sniffsniff.core.common.settings.DataStoreSettingsRepository
import com.rynekryz.sniffsniff.core.common.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
