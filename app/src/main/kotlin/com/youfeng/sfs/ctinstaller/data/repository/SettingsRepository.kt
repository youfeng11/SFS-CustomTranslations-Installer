package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.youfeng.sfs.ctinstaller.data.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SettingsRepository 的接口。
 * ViewModel 将依赖这个接口，而不是具体的实现。
 */
interface SettingsRepository {

    /**
     * (关键变更) 暴露一个组合了所有设置的 Flow。
     */
    val userSettings: Flow<UserSettings>

    /**
     * 写入“启用深色主题”
     */
    suspend fun setDarkTheme(isEnabled: Boolean)

    /**
     * 写入“跟随系统”
     */
    suspend fun setFollowingSystem(isEnabled: Boolean)

    /**
     * 写入“检查更新”
     */
    suspend fun setCheckUpdate(isEnabled: Boolean)

    /**
     * 写入“自定义SU命令”
     */
    suspend fun setCustomSuCommand(command: String)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository { // <-- (关键变更) 实现接口

    private object PreferencesKeys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val IS_FOLLOWING_SYSTEM = booleanPreferencesKey("is_following_system")
        val CHECK_UPDATE = booleanPreferencesKey("check_update")
        val CUSTOM_SU_COMMAND = stringPreferencesKey("custom_su_command")
    }

    // (关键变更) 在 Repository 内部组合 Flow
    override val userSettings: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            // 从 Preferences 映射到 UserSettings 领域模型
            val isDarkTheme = preferences[PreferencesKeys.IS_DARK_THEME] ?: false
            val isFollowingSystem = preferences[PreferencesKeys.IS_FOLLOWING_SYSTEM] ?: true
            val checkUpdate = preferences[PreferencesKeys.CHECK_UPDATE] ?: true
            val customSuCommand = preferences[PreferencesKeys.CUSTOM_SU_COMMAND] ?: ""
            
            UserSettings(
                isDarkThemeEnabled = isDarkTheme,
                isFollowingSystem = isFollowingSystem,
                checkUpdate = checkUpdate,
                customSuCommand = customSuCommand
            )
        }

    // (关键变更) 为所有公共方法添加 'override'
    override suspend fun setDarkTheme(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isEnabled
        }
    }

    override suspend fun setFollowingSystem(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLLOWING_SYSTEM] = isEnabled
        }
    }

    override suspend fun setCheckUpdate(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE] = isEnabled
        }
    }
    
    override suspend fun setCustomSuCommand(command: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_SU_COMMAND] = command
        }
    }
}