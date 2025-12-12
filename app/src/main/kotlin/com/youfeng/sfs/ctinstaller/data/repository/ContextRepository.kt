package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import com.youfeng.sfs.ctinstaller.util.UiText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ContextRepository {
    fun getString(uiText: UiText): String
}

@Singleton
class ContextRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ContextRepository {
    override fun getString(uiText: UiText): String =
        uiText.asString(context)
}