package com.mgt.downloader.extractor

import com.mgt.downloader.base.HasDisposable
import com.mgt.downloader.base.HttpPostMultipart
import com.mgt.downloader.base.WebHtmlExtractor
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.TAG
import com.mgt.downloader.utils.logD
import kotlin.reflect.KClass


class FacebookExtractor(hasDisposable: HasDisposable) :
    WebHtmlExtractor<ExtractorConfig>(hasDisposable) {
    override val extractorName = "facebook"
    override val extractorConfigClass: KClass<ExtractorConfig>
        get() = ExtractorConfig::class

    override fun extract(url: String): FilePreviewInfo {
        return extract(url, getTargetWebContent(url))
    }

    private fun getTargetWebContent(url: String): String {
        return with(HttpPostMultipart(API_URL, "utf-8", HashMap())) {
            addFormField("url", url)
            finish()
        }.also { logD(TAG, "getTargetWebContent, url: $url") }
    }

    companion object {
        private const val API_URL = "https://www.getfvid.com/vi/downloader"
    }
}