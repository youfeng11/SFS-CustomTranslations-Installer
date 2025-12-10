package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import com.youfeng.sfs.ctinstaller.utils.UiText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getString(uiText: UiText): String {
        return uiText.asString(context)
    }
}