@file:Suppress("unused")

package com.youfeng.sfs.ctinstaller.di

import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}