@file:Suppress("unused")

package com.youfeng.sfs.ctinstaller.di

import com.youfeng.sfs.ctinstaller.data.repository.ContextRepository
import com.youfeng.sfs.ctinstaller.data.repository.ContextRepositoryImpl
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepository
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepositoryImpl
import com.youfeng.sfs.ctinstaller.data.repository.InstallationRepository
import com.youfeng.sfs.ctinstaller.data.repository.InstallationRepositoryImpl
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepositoryImpl
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepositoryImpl
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepository
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindContextRepository(
        contextRepositoryImpl: ContextRepositoryImpl
    ): ContextRepository

    @Binds
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository

    @Binds
    abstract fun bindInstallationRepository(
        installationRepositoryImpl: InstallationRepositoryImpl
    ): InstallationRepository

    @Binds
    abstract fun bindNetworkRepository(
        networkRepositoryImpl: NetworkRepositoryImpl
    ): NetworkRepository

    @Binds
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    abstract fun bindShizukuRepository(
        shizukuRepositoryImpl: ShizukuRepositoryImpl
    ): ShizukuRepository
}