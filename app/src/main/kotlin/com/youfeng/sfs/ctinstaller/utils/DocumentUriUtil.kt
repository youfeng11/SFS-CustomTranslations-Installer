package com.youfeng.sfs.ctinstaller.utils

import android.net.Uri
import android.provider.DocumentsContract
import com.youfeng.sfs.ctinstaller.core.Constants

object DocumentUriUtil {
    fun buildAndroidData(packageName: String): Uri? {
        val androidData =
            if (ExploitFileUtil.isExploitable) "Android/da${ExploitFileUtil.ZWSP}ta" else
                Constants.ANDROID_DATA_DIRECTORY
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$androidData/$packageName"
        )
    }

    fun buildAndroidDataInit(packageName: String): Uri? {
        val dataUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary"
        )
        val androidData =
            if (ExploitFileUtil.isExploitable) "Android/da${ExploitFileUtil.ZWSP}ta" else
                Constants.ANDROID_DATA_DIRECTORY
        val packageDocId = "primary:/$androidData/$packageName"
        return DocumentsContract.buildDocumentUriUsingTree(dataUri, packageDocId)
    }
}