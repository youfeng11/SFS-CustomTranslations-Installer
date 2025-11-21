package com.youfeng.sfs.ctinstaller.di

import android.content.Context
import com.youfeng.sfs.ctinstaller.timber.FileLoggingTree
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

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