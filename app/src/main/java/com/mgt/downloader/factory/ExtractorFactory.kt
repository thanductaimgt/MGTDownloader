package com.mgt.downloader.factory

import com.mgt.downloader.base.Extractor
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.extractor.*
import com.mgt.downloader.utils.Utils

object ExtractorFactory {
    fun create(hasDisposable: HasDisposable, url:String):Extractor{
        return when {
            Utils.isTikTokUrl(url) -> {
                TikTokExtractor(hasDisposable)
            }
            Utils.isFacebookUrl(url) -> {
                FacebookExtractor(hasDisposable)
            }
            Utils.isBobaUrl(url) -> {
                BobaExtractor(hasDisposable)
            }
            Utils.isTwitterUrl(url) -> {
                TwitterExtractor(hasDisposable)
            }
            Utils.isInstaUrl(url) -> {
                InstagramExtractor(hasDisposable)
            }
            else -> {
                OtherFileExtractor(hasDisposable)
            }
        }
    }
}