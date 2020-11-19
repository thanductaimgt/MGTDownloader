package com.mgt.downloader

import com.mgt.downloader.utils.Utils

object ExtractContentManager {
    fun isTarget(url:String, webContent:String):Boolean{
        return when{
            Utils.isTikTokUrl(url)->webContent.contains("download server 01", true)
            else->false
        }
    }
}