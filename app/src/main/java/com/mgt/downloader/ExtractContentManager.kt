package com.mgt.downloader

import com.mgt.downloader.di.DI.utils

class ExtractContentManager {
    fun isTarget(url: String, webContent: String): Boolean {
        return when {
            utils.isTikTokUrl(url) -> webContent.contains("download server 01", true)
            else -> false
        }
    }
}