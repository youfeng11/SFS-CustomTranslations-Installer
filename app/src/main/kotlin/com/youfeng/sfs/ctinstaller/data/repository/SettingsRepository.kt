package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")

        // 1. (新增) "跟随系统" 的 Key
        val IS_FOLLOWING_SYSTEM = booleanPreferencesKey("is_following_system")

        val CHECK_UPDATE = booleanPreferencesKey("check_update")
    }

    // “启用深色主题”的 Flow (手动设置)
    val isDarkThemeEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] ?: false
        }

    // 2. (新增) “跟随系统”的 Flow
    //    请注意: 默认值我们设为 true, 这是一个更友好的默认行为
    val isFollowingSystem: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_FOLLOWING_SYSTEM] ?: true
        }

    val checkUpdate: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE] ?: true
        }

    // 写入“启用深色主题”
    suspend fun setDarkTheme(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isEnabled
        }
    }

    // 3. (新增) 写入“跟随系统”
    suspend fun setFollowingSystem(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLLOWING_SYSTEM] = isEnabled
        }
    }

    suspend fun setCheckUpdate(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE] = isEnabled
        }
    }
}