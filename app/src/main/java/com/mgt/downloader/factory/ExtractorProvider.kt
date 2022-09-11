package com.mgt.downloader.factory

import com.mgt.downloader.base.Extractor
import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.extractor.*
import com.mgt.downloader.extractor.tiktok.TikTokExtractorV2
import com.mgt.downloader.serialize_model.ExtractorConfig

class ExtractorProvider {
    fun provideExtractor(
        hasDisposable: HasDisposable,
        url: String
    ): Extractor<out ExtractorConfig> {
        return when {
            utils.isTikTokUrl(url) -> {
                TikTokExtractorV2(hasDisposable)
            }
            utils.isFacebookUrl(url) -> {
                FacebookExtractor(hasDisposable)
            }
            utils.isBobaUrl(url) -> {
                BobaExtractor(hasDisposable)
            }
            utils.isTwitterUrl(url) -> {
                TwitterExtractor(hasDisposable)
            }
            utils.isInstaUrl(url) -> {
                InstagramExtractor(hasDisposable)
            }
            else -> {
                OtherFileExtractor(hasDisposable)
            }
        }
    }
}