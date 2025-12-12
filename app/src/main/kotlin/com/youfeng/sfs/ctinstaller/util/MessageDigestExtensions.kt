package com.youfeng.sfs.ctinstaller.util

import okio.ByteString.Companion.encodeUtf8

fun String.md5(): String = encodeUtf8().md5().hex()
