package com.youfeng.sfs.ctinstaller.di

import android.content.Context
import com.youfeng.sfs.ctinstaller.timber.FileLoggingTree
import com.youfeng.sfs.ctinstaller.BuildConfig
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import timber.log.Timber
import com.youfeng.sfs.ctinstaller.utils.toast

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFileLoggingTree(@ApplicationContext context: Context) =
        FileLoggingTree(context).also {
            Timber.plant(it)
        }
}