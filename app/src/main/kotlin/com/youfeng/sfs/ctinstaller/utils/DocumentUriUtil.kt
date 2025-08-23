package com.youfeng.sfs.ctinstaller.utils

import android.net.Uri
import android.provider.DocumentsContract
import com.youfeng.sfs.ctinstaller.core.Constants

object DocumentUriUtil {
    fun buildAndroidData(packageName: String): Uri? {
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Constants.ANDROID_DATA_DIRECTORY}/$packageName"
        )
    }

    fun buildAndroidDataInit(packageName: String): Uri? {
        val dataUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary"
        )
        val packageDocId = "primary:${Constants.ANDROID_DATA_DIRECTORY}/$packageName"
        return DocumentsContract.buildDocumentUriUsingTree(dataUri, packageDocId)
    }
}